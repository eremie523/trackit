# HelixMind Triage Inference Service

Stateless FastAPI microservice that accepts a 30-second ECG/PPG window and
returns a triage result. Called by the TrackIt Java backend — the browser
never touches it directly.

## File structure

```
inference-service/
  app/
    __init__.py
    model.py             — WearableTriageModel + signal constants
    signal_processing.py — Preprocessors, FeatureExtractor, SeverityEngine,
                           generate_ecg, generate_ppg
    dataset.py           — WearableDataset (synthetic training data)
    main.py              — FastAPI app
  training/
    __init__.py
    train.py             — Training script
    generate_demo_csv.py — Demo CSV generator
  checkpoints/           — Model checkpoints (created by train.py)
  demo_data/             — Demo CSV files (created by generate_demo_csv.py)
  requirements.txt
```

## Quick start

```bash
# 1. Install dependencies
pip install -r requirements.txt

# 2. Train the model (~5 min on CPU)
cd inference-service
python -m training.train

# 3. Generate demo CSV files for the frontend
python -m training.generate_demo_csv

# 4. Start the inference service
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

## API

### `GET /health`

```json
{"status": "ok", "model_loaded": true}
```

### `POST /infer`

**Request body:**
```json
{
  "ecg": [<float>, ...],   // exactly 7680 values (256 Hz × 30 s), or omit
  "ppg": [<float>, ...]    // exactly 1920 values (64 Hz × 30 s), or omit
}
```

At least one field must be present. Returns HTTP 400 with a descriptive
message if either array has the wrong length.

**Response body (flat JSON — no wrapper):**
```json
{
  "timestamp":          "2024-01-01 12:00:00",
  "severity":           "GREEN | YELLOW | RED",
  "severityScore":      0.12,
  "rhythmLabel":        "Normal",
  "rhythmProbs":        {"Normal": 0.91, "AFib": 0.03, ...},
  "heartRate":          72.0,
  "hrvRmssd":           42.3,
  "spo2":               97.8,
  "stressLevel":        "Low",
  "stressProbs":        {"Low": 0.85, "Medium": 0.12, "High": 0.03},
  "requiresEscalation": false,
  "escalationReason":   ""
}
```

## Demo scenarios

| File | Contents |
|---|---|
| `normal_steady.csv` | 60 s Normal (HR 72, SpO2 98, stress Low) |
| `afib_episode.csv` | 30 s Normal → 60 s AFib (HR 115, SpO2 93, stress High) |
| `bradycardia_critical.csv` | 30 s Normal → 45 s Bradycardia (HR 38, SpO2 89, stress Medium) |
| `tachycardia_stress.csv` | 30 s Normal → 45 s Tachycardia (HR 140, SpO2 95, stress High) |

Each file is long-format CSV (`sample_index,time_sec,channel,value`).
The frontend replays it sample-by-sample over the Java WebSocket backend,
which buffers 30-second windows and calls this service every 5 seconds.
