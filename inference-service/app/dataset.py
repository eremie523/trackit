"""
app/dataset.py
--------------
WearableDataset — synthetic ECG/PPG dataset for training the triage model.
Extracted verbatim from ecg_wearable_triage.ipynb § 12.

Uses generate_ecg / generate_ppg from signal_processing to create raw signals,
then applies the module-level ecg_prep / ppg_prep instances for preprocessing,
matching the notebook's behaviour exactly.
"""

import numpy as np
import torch
from torch.utils.data import Dataset

from app.model import RHYTHM_CLASSES, STRESS_CLASSES
from app.signal_processing import (
    ecg_prep, ppg_prep,
    generate_ecg, generate_ppg,
    HR_CRITICAL_LOW, HR_CRITICAL_HIGH,
    HR_BRADY_THRESH, HR_TACHY_THRESH,
    SPO2_WARN_THRESH, SPO2_CRIT_THRESH,
)


class WearableDataset(Dataset):
    """
    Synthetic wearable dataset.
    Replace generate_* calls with real data loading when available.

    Each sample contains:
        ecg           : (1, ECG_LEN) float tensor — preprocessed
        ppg           : (1, PPG_LEN) float tensor — preprocessed
        rhythm_label  : scalar long tensor — index into RHYTHM_CLASSES
        stress_label  : scalar long tensor — index into STRESS_CLASSES
        spo2          : (1,) float tensor — SpO2 percentage
        severity      : (1,) float tensor — ground-truth severity score [0, 1]
    """

    def __init__(self, n_samples: int = 2000):
        self.samples = []
        rhythm_weights = [0.45, 0.2, 0.1, 0.15, 0.1]   # class balance

        for _ in range(n_samples):
            rhythm = np.random.choice(RHYTHM_CLASSES, p=rhythm_weights)
            stress = np.random.choice(STRESS_CLASSES, p=[0.5, 0.35, 0.15])

            # HR varies by rhythm
            hr_map = {
                'Normal':      np.random.uniform(60, 100),
                'AFib':        np.random.uniform(60, 130),
                'Bradycardia': np.random.uniform(HR_CRITICAL_LOW, HR_BRADY_THRESH),
                'Tachycardia': np.random.uniform(HR_TACHY_THRESH, HR_CRITICAL_HIGH),
                'Anomaly':     np.random.uniform(50, 130)
            }
            hr = hr_map[rhythm]

            # SpO2 varies by severity
            if rhythm in ['Bradycardia', 'Anomaly'] and np.random.rand() < 0.3:
                spo2 = np.random.uniform(85, SPO2_WARN_THRESH)
            else:
                spo2 = np.random.uniform(95, 100)

            # Generate raw signals and preprocess
            ecg_raw = generate_ecg(rhythm)
            ppg_raw = generate_ppg(hr, spo2, stress)
            ecg = ecg_prep(ecg_raw)
            ppg = ppg_prep(ppg_raw)

            # Compute severity label (ground truth for training)
            severity = self._compute_severity(rhythm, hr, spo2, stress)

            self.samples.append({
                'ecg':          ecg,
                'ppg':          ppg,
                'rhythm_label': RHYTHM_CLASSES.index(rhythm),
                'stress_label': STRESS_CLASSES.index(stress),
                'spo2':         spo2,
                'severity':     severity
            })

    @staticmethod
    def _compute_severity(rhythm, hr, spo2, stress) -> float:
        score = 0.0
        if rhythm == 'AFib':        score += 0.45
        if rhythm == 'Anomaly':     score += 0.35
        if rhythm == 'Tachycardia': score += 0.30
        if rhythm == 'Bradycardia': score += 0.30
        if hr < HR_CRITICAL_LOW:    score += 0.40
        if hr > HR_CRITICAL_HIGH:   score += 0.35
        if spo2 < SPO2_CRIT_THRESH: score += 0.40
        elif spo2 < SPO2_WARN_THRESH: score += 0.20
        if stress == 'High':        score += 0.15
        elif stress == 'Medium':    score += 0.05
        return float(np.clip(score, 0.0, 1.0))

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        s = self.samples[idx]
        return {
            'ecg':          torch.FloatTensor(s['ecg']).unsqueeze(0),
            'ppg':          torch.FloatTensor(s['ppg']).unsqueeze(0),
            'rhythm_label': torch.LongTensor([s['rhythm_label']]).squeeze(),
            'stress_label': torch.LongTensor([s['stress_label']]).squeeze(),
            'spo2':         torch.FloatTensor([s['spo2']]),
            'severity':     torch.FloatTensor([s['severity']])
        }
