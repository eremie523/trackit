"""
training/generate_demo_csv.py
------------------------------
Generates four demo CSV files for the frontend to replay, each containing
a sequence of 30-second segments concatenated into one long-format CSV.

The rhythm changes mid-file so the live dashboard actually shows severity
tiers updating as the frontend streams the data.

Run from the inference-service/ directory:
    python -m training.generate_demo_csv

Output (in demo_data/)
----------------------
normal_steady.csv         60s Normal
afib_episode.csv          30s Normal → 60s AFib
bradycardia_critical.csv  30s Normal → 45s Bradycardia
tachycardia_stress.csv    30s Normal → 45s Tachycardia

CSV format — long-format, one row per sample:
    sample_index,time_sec,channel,value
    0,0.0,ecg,0.088823
    ...
    7680,0.0,ppg,0.412...

ECG rows first (all of them), then PPG rows.
ECG time_sec = sample_index / 256
PPG time_sec = sample_index / 64
"""

import sys
import csv
import numpy as np
from pathlib import Path

# Allow running as python -m training.generate_demo_csv from inference-service/
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.signal_processing import generate_ecg, generate_ppg
from app.model import ECG_FS, PPG_FS, WINDOW_SEC

DEMO_DATA_DIR = Path(__file__).resolve().parent.parent / 'demo_data'

# Reproducibility seed (per brief)
np.random.seed(7)


def _build_scenario(segments: list) -> tuple:
    """
    Concatenate multiple 30-second segments into one long ECG+PPG array pair.

    segments : list of dicts with keys:
        duration_sec, rhythm, hr, spo2, stress

    Returns (ecg_full, ppg_full) as 1-D numpy arrays.
    """
    ecg_parts = []
    ppg_parts = []

    for seg in segments:
        dur = seg['duration_sec']
        ecg_parts.append(generate_ecg(seg['rhythm'], fs=ECG_FS, duration=dur))
        ppg_parts.append(generate_ppg(seg['hr'], seg['spo2'], seg['stress'],
                                       fs=PPG_FS, duration=dur))

    return np.concatenate(ecg_parts), np.concatenate(ppg_parts)


def _write_csv(filename: str, ecg: np.ndarray, ppg: np.ndarray):
    """Write long-format CSV: all ECG rows first, then all PPG rows."""
    out_path = DEMO_DATA_DIR / filename
    with open(out_path, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['sample_index', 'time_sec', 'channel', 'value'])

        # ECG rows
        for i, v in enumerate(ecg):
            writer.writerow([i, round(i / ECG_FS, 5), 'ecg', round(float(v), 6)])

        # PPG rows
        for i, v in enumerate(ppg):
            writer.writerow([i, round(i / PPG_FS, 5), 'ppg', round(float(v), 6)])

    ecg_dur = len(ecg) / ECG_FS
    ppg_dur = len(ppg) / PPG_FS
    print(f'  ✓ {filename}  ({ecg_dur:.0f}s ECG / {ppg_dur:.0f}s PPG)')


def generate_all():
    DEMO_DATA_DIR.mkdir(parents=True, exist_ok=True)
    print(f'Generating demo CSV files → {DEMO_DATA_DIR}\n')

    # ── 1. normal_steady.csv — 60s Normal ─────────────────────────────────
    ecg, ppg = _build_scenario([
        dict(duration_sec=60, rhythm='Normal', hr=72, spo2=98, stress='Low'),
    ])
    _write_csv('normal_steady.csv', ecg, ppg)

    # ── 2. afib_episode.csv — 30s Normal → 60s AFib ───────────────────────
    ecg, ppg = _build_scenario([
        dict(duration_sec=30, rhythm='Normal', hr=72,  spo2=98, stress='Low'),
        dict(duration_sec=60, rhythm='AFib',   hr=115, spo2=93, stress='High'),
    ])
    _write_csv('afib_episode.csv', ecg, ppg)

    # ── 3. bradycardia_critical.csv — 30s Normal → 45s Bradycardia ────────
    ecg, ppg = _build_scenario([
        dict(duration_sec=30, rhythm='Normal',      hr=72, spo2=98, stress='Low'),
        dict(duration_sec=45, rhythm='Bradycardia', hr=38, spo2=89, stress='Medium'),
    ])
    _write_csv('bradycardia_critical.csv', ecg, ppg)

    # ── 4. tachycardia_stress.csv — 30s Normal → 45s Tachycardia ─────────
    ecg, ppg = _build_scenario([
        dict(duration_sec=30, rhythm='Normal',      hr=72,  spo2=98, stress='Low'),
        dict(duration_sec=45, rhythm='Tachycardia', hr=140, spo2=95, stress='High'),
    ])
    _write_csv('tachycardia_stress.csv', ecg, ppg)

    print('\nAll demo CSV files generated successfully.')


if __name__ == '__main__':
    generate_all()
