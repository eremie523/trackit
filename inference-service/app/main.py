"""
app/main.py
-----------
FastAPI inference microservice.

Endpoints
---------
GET  /health   — liveness check, reports whether the model is loaded
POST /infer    — accepts a 30-second ECG/PPG window, returns TriageResult JSON

Design constraints (per brief)
-------------------------------
* Model is loaded ONCE at startup into a module-level variable — never per-request.
* /infer uses a plain `def` (not async def) because PyTorch CPU inference is
  synchronous and blocking; async here would give false concurrency.
* No CORS headers — this service is called only by the Java backend.
* No auth, no database, no middleware.
* Response body is exactly TriageResult.to_dict() — no wrapper, no nesting.
"""

import os
import torch
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List

from app.model import WearableTriageModel, ECG_LEN, PPG_LEN
from app.signal_processing import SeverityEngine

# ── Paths ─────────────────────────────────────────────────────────────────────
# Resolved relative to the inference-service/ root (wherever uvicorn is launched)
SERVICE_ROOT    = Path(__file__).resolve().parent.parent
CHECKPOINT_PATH = SERVICE_ROOT / 'checkpoints' / 'helixmind_triage.pt'

# ── Module-level state ────────────────────────────────────────────────────────
# Populated at startup; None signals that loading failed.
_severity_engine: Optional[SeverityEngine] = None
_model_loaded: bool = False

# ── FastAPI app ───────────────────────────────────────────────────────────────
app = FastAPI(
    title='HelixMind Triage Inference Service',
    description='Stateless ECG/PPG triage microservice — called by the TrackIt Java backend.',
    version='1.0.0',
)


@app.on_event('startup')
def load_model():
    """Load the model checkpoint once at application startup."""
    global _severity_engine, _model_loaded

    if not CHECKPOINT_PATH.exists():
        raise RuntimeError(
            f'Checkpoint not found at {CHECKPOINT_PATH}. '
            'Run the training script first:\n'
            '  cd inference-service\n'
            '  python -m training.train'
        )

    checkpoint = torch.load(str(CHECKPOINT_PATH), map_location='cpu')
    model = WearableTriageModel()
    model.load_state_dict(checkpoint['model_state_dict'])
    model.eval()

    _severity_engine = SeverityEngine(model, device='cpu')
    _model_loaded = True
    print(f'[startup] Model loaded from {CHECKPOINT_PATH}')


# ── Request / response schemas ────────────────────────────────────────────────

class InferRequest(BaseModel):
    ecg: Optional[List[float]] = None   # exactly ECG_LEN values, or omit
    ppg: Optional[List[float]] = None   # exactly PPG_LEN values, or omit


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get('/health')
def health():
    """Liveness check."""
    return {'status': 'ok', 'model_loaded': _model_loaded}


@app.post('/infer')
def infer(req: InferRequest):
    """
    Run triage inference on a 30-second ECG/PPG window.

    At least one of `ecg` or `ppg` must be present.
    ECG must be exactly 7680 values (256 Hz × 30 s).
    PPG must be exactly 1920 values (64 Hz × 30 s).

    Returns the TriageResult as a flat JSON object.
    """
    # ── Validation ────────────────────────────────────────────────────────
    if req.ecg is None and req.ppg is None:
        raise HTTPException(
            status_code=400,
            detail='At least one of "ecg" or "ppg" must be provided.'
        )

    if req.ecg is not None and len(req.ecg) != ECG_LEN:
        raise HTTPException(
            status_code=400,
            detail=f'ECG array must contain exactly {ECG_LEN} values '
                   f'(256 Hz × 30 s), got {len(req.ecg)}.'
        )

    if req.ppg is not None and len(req.ppg) != PPG_LEN:
        raise HTTPException(
            status_code=400,
            detail=f'PPG array must contain exactly {PPG_LEN} values '
                   f'(64 Hz × 30 s), got {len(req.ppg)}.'
        )

    # ── Inference ─────────────────────────────────────────────────────────
    import numpy as np

    # Build numpy arrays; use zeros for the absent modality so SeverityEngine
    # still gets valid-shape inputs.  The engine blends PPG vitals with model
    # predictions, so zeros produce neutral (not catastrophically wrong) vitals.
    ecg_arr = np.array(req.ecg, dtype=np.float32) if req.ecg is not None \
              else np.zeros(ECG_LEN, dtype=np.float32)
    ppg_arr = np.array(req.ppg, dtype=np.float32) if req.ppg is not None \
              else np.zeros(PPG_LEN, dtype=np.float32)

    try:
        result = _severity_engine.evaluate(ecg_arr, ppg_arr)
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f'Inference failed: {exc}'
        )

    return result.to_dict()
