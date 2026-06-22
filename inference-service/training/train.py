"""
training/train.py
-----------------
Trains WearableTriageModel on a synthetic dataset and saves the checkpoint.

Run from the inference-service/ directory:
    python -m training.train

Output
------
checkpoints/helixmind_triage.pt — self-documenting checkpoint containing:
    model_state_dict, ecg_fs, ppg_fs, window_sec, rhythm_classes, stress_classes
"""

import os
import sys
import math
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, random_split
from torch.optim import AdamW
from torch.optim.lr_scheduler import OneCycleLR
from pathlib import Path

# Allow running as python -m training.train from inference-service/
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.model import (
    WearableTriageModel,
    ECG_FS, PPG_FS, WINDOW_SEC,
    RHYTHM_CLASSES, STRESS_CLASSES,
)
from app.dataset import WearableDataset

# ── Training config (exact values from notebook) ──────────────────────────────
N_SAMPLES    = 2000
TRAIN_RATIO  = 0.80
SEED         = 42
EPOCHS       = 40
BATCH_SIZE   = 64
LR           = 2e-3
WEIGHT_DECAY = 1e-4

CHECKPOINT_DIR  = Path(__file__).resolve().parent.parent / 'checkpoints'
BEST_CKPT_PATH  = CHECKPOINT_DIR / 'best_val.pt'
FINAL_CKPT_PATH = CHECKPOINT_DIR / 'helixmind_triage.pt'

DEVICE = 'cpu'   # CPU only — no GPU needed for the demo


# ── Multi-task loss (exact weights from notebook) ─────────────────────────────

def multi_task_loss(outputs: dict, batch: dict) -> tuple:
    """
    Combined loss across all four prediction heads.
    Each task is weighted by clinical importance.

    Weights (from notebook):
        1.5 × rhythm_CE + 1.0 × severity_BCE + 0.8 × stress_CE + 0.5 × spo2_MSE
    """
    rhythm_loss = F.cross_entropy(
        outputs['rhythm_logits'], batch['rhythm_label'].to(DEVICE)
    )
    stress_loss = F.cross_entropy(
        outputs['stress_logits'], batch['stress_label'].to(DEVICE)
    )
    spo2_loss = F.mse_loss(
        outputs['spo2_pred'].squeeze(), batch['spo2'].squeeze().to(DEVICE)
    ) / 100.0     # normalise % scale
    severity_loss = F.binary_cross_entropy(
        outputs['severity_score'].squeeze(), batch['severity'].squeeze().to(DEVICE)
    )

    total = (1.5 * rhythm_loss +
             1.0 * severity_loss +
             0.8 * stress_loss +
             0.5 * spo2_loss)

    return total, {
        'rhythm':   rhythm_loss.item(),
        'stress':   stress_loss.item(),
        'spo2':     spo2_loss.item(),
        'severity': severity_loss.item()
    }


# ── Training loop ─────────────────────────────────────────────────────────────

def train():
    torch.manual_seed(SEED)
    np.random.seed(SEED)

    CHECKPOINT_DIR.mkdir(parents=True, exist_ok=True)

    print('Building synthetic dataset...')
    full_ds = WearableDataset(n_samples=N_SAMPLES)
    train_size = int(TRAIN_RATIO * len(full_ds))
    val_size   = len(full_ds) - train_size
    split_gen  = torch.Generator().manual_seed(SEED)
    train_ds, val_ds = random_split(full_ds, [train_size, val_size], generator=split_gen)

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True,  num_workers=0)
    val_loader   = DataLoader(val_ds,   batch_size=BATCH_SIZE, shuffle=False, num_workers=0)
    print(f'Dataset ready — train: {train_size}  val: {val_size}')

    model = WearableTriageModel().to(DEVICE)
    n_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f'Model parameters: {n_params:,}  (target: <500,000)')
    assert n_params < 500_000, f'Model too large: {n_params:,} params'

    optimizer = AdamW(model.parameters(), lr=LR, weight_decay=WEIGHT_DECAY)
    scheduler = OneCycleLR(optimizer, max_lr=LR,
                           steps_per_epoch=len(train_loader), epochs=EPOCHS)

    best_val = float('inf')

    for epoch in range(1, EPOCHS + 1):
        # ── Train ──
        model.train()
        tr_loss, tr_correct, tr_total = 0.0, 0, 0
        for batch in train_loader:
            ecg  = batch['ecg'].to(DEVICE)
            ppg  = batch['ppg'].to(DEVICE)
            outputs = model(ecg, ppg)
            loss, _ = multi_task_loss(outputs, batch)
            optimizer.zero_grad()
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            tr_loss    += loss.item() * ecg.size(0)
            preds       = outputs['rhythm_logits'].argmax(dim=1)
            tr_correct += (preds == batch['rhythm_label'].to(DEVICE)).sum().item()
            tr_total   += ecg.size(0)

        # ── Validate ──
        model.eval()
        vl_loss, vl_correct, vl_total = 0.0, 0, 0
        with torch.no_grad():
            for batch in val_loader:
                ecg  = batch['ecg'].to(DEVICE)
                ppg  = batch['ppg'].to(DEVICE)
                outputs = model(ecg, ppg)
                loss, _ = multi_task_loss(outputs, batch)
                vl_loss    += loss.item() * ecg.size(0)
                preds       = outputs['rhythm_logits'].argmax(dim=1)
                vl_correct += (preds == batch['rhythm_label'].to(DEVICE)).sum().item()
                vl_total   += ecg.size(0)

        tr_loss /= tr_total
        vl_loss /= vl_total
        tr_acc   = tr_correct / tr_total
        vl_acc   = vl_correct / vl_total

        # Save best checkpoint by val loss
        if vl_loss < best_val:
            best_val = vl_loss
            torch.save(model.state_dict(), str(BEST_CKPT_PATH))

        if epoch % 5 == 0 or epoch == 1:
            print(f'Epoch {epoch:3d}/{EPOCHS}  '
                  f'Loss: {tr_loss:.4f}/{vl_loss:.4f}  '
                  f'Rhythm Acc: {tr_acc:.3f}/{vl_acc:.3f}')

    print(f'\nBest val loss: {best_val:.4f}')

    # ── Load best weights, save self-documenting final checkpoint ────────────
    model.load_state_dict(torch.load(str(BEST_CKPT_PATH), map_location=DEVICE))
    torch.save({
        'model_state_dict': model.state_dict(),
        'ecg_fs':           ECG_FS,
        'ppg_fs':           PPG_FS,
        'window_sec':       WINDOW_SEC,
        'rhythm_classes':   RHYTHM_CLASSES,
        'stress_classes':   STRESS_CLASSES,
    }, str(FINAL_CKPT_PATH))
    print(f'Final checkpoint saved → {FINAL_CKPT_PATH}')

    # Clean up temp best checkpoint
    if BEST_CKPT_PATH.exists():
        BEST_CKPT_PATH.unlink()


if __name__ == '__main__':
    train()
