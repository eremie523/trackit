"""
app/signal_processing.py
------------------------
Signal preprocessors, feature extractor, severity engine, and synthetic signal
generators — all extracted verbatim from ecg_wearable_triage.ipynb.

Key design notes
----------------
* ECGPreprocessor / PPGPreprocessor are instantiated once at module level
  (ecg_prep / ppg_prep) so their Butterworth filter coefficients are computed
  only once.
* generate_ecg / generate_ppg return RAW, UNPREPROCESSED arrays.
  The notebook originally called ecg_prep / ppg_prep at the end of each
  generator, which was intentional for the notebook demo but wrong for the
  service (SeverityEngine.evaluate() preprocesses internally).  Those calls
  are removed here per the brief.
* SeverityEngine.evaluate() accepts raw numpy arrays and applies preprocessing
  internally before running the model.
"""

import numpy as np
import torch
import torch.nn.functional as F
from scipy import signal as scipy_signal
from dataclasses import dataclass
from typing import Optional, Dict, Tuple
from enum import Enum
from datetime import datetime

from app.model import (
    ECG_FS, PPG_FS, WINDOW_SEC,
    RHYTHM_CLASSES, STRESS_CLASSES,
    WearableTriageModel,
)

# ── Physiological thresholds (from notebook § System Configuration) ───────────
HR_BRADY_THRESH  = 50       # bpm — below this → Bradycardia alert
HR_TACHY_THRESH  = 120      # bpm — above this → Tachycardia alert
HR_CRITICAL_LOW  = 40       # bpm — immediate emergency
HR_CRITICAL_HIGH = 150      # bpm — immediate emergency
SPO2_WARN_THRESH = 95.0     # % — below this → warning
SPO2_CRIT_THRESH = 90.0     # % — below this → emergency
HRV_LOW_THRESH   = 20.0     # ms RMSSD — below this → high stress

YELLOW_THRESH = 0.40        # severity score → YELLOW
RED_THRESH    = 0.72        # severity score → RED / emergency


# ── Severity tier enum ────────────────────────────────────────────────────────

class Severity(Enum):
    GREEN  = 'GREEN'    # Normal — continue monitoring
    YELLOW = 'YELLOW'   # Warning — alert user, recommend clinical ECG
    RED    = 'RED'      # Critical — emergency escalation


# ── TriageResult dataclass ────────────────────────────────────────────────────

@dataclass
class TriageResult:
    """
    Complete output from a single triage inference window.
    All downstream logic (alerts, escalation) is driven by this object.
    """
    timestamp:          str
    severity:           Severity
    severity_score:     float              # 0.0 (normal) → 1.0 (critical)

    # Rhythm
    rhythm_label:       str
    rhythm_probs:       Dict[str, float]   # per-class probabilities

    # Vitals
    heart_rate:         float              # bpm
    hrv_rmssd:          float              # ms
    spo2:               float             # %
    stress_level:       str               # Low / Medium / High
    stress_probs:       Dict[str, float]

    # Escalation
    requires_escalation: bool = False
    escalation_reason:   str  = ''

    def to_dict(self) -> dict:
        """Serialise to a plain dict for JSON responses. FastAPI uses this."""
        return {
            'timestamp':          self.timestamp,
            'severity':           self.severity.value,
            'severityScore':      self.severity_score,
            'rhythmLabel':        self.rhythm_label,
            'rhythmProbs':        self.rhythm_probs,
            'heartRate':          self.heart_rate,
            'hrvRmssd':           self.hrv_rmssd,
            'spo2':               self.spo2,
            'stressLevel':        self.stress_level,
            'stressProbs':        self.stress_probs,
            'requiresEscalation': self.requires_escalation,
            'escalationReason':   self.escalation_reason,
        }


# ── Preprocessors ─────────────────────────────────────────────────────────────

class ECGPreprocessor:
    """
    Cleans raw single-lead ECG from wearable.
    Handles baseline wander, powerline noise, and motion artefacts.
    """
    def __init__(self, fs: int = ECG_FS):
        self.fs = fs
        # Bandpass 0.5–40 Hz removes baseline wander + high-freq noise
        self.sos = scipy_signal.butter(
            4, [0.5, 40.0], btype='bandpass', fs=fs, output='sos'
        )

    def __call__(self, ecg: np.ndarray) -> np.ndarray:
        ecg = np.nan_to_num(ecg.astype(np.float32))
        ecg = scipy_signal.sosfiltfilt(self.sos, ecg)   # zero-phase filter
        # Clip extreme artefacts (> 5 std)
        mu, std = ecg.mean(), ecg.std() + 1e-8
        ecg = np.clip(ecg, mu - 5*std, mu + 5*std)
        # Z-score normalise
        return (ecg - mu) / std


class PPGPreprocessor:
    """
    Cleans raw PPG signal from optical wearable sensor.
    Removes DC offset and motion artefacts via bandpass filter.
    """
    def __init__(self, fs: int = PPG_FS):
        self.fs = fs
        # Bandpass 0.5–8 Hz — keeps cardiac pulse, removes motion/breathing
        self.sos = scipy_signal.butter(
            4, [0.5, 8.0], btype='bandpass', fs=fs, output='sos'
        )

    def __call__(self, ppg: np.ndarray) -> np.ndarray:
        ppg = np.nan_to_num(ppg.astype(np.float32))
        ppg = scipy_signal.sosfiltfilt(self.sos, ppg)
        mu, std = ppg.mean(), ppg.std() + 1e-8
        return (ppg - mu) / std


# Module-level singleton instances — filter coefficients computed once
ecg_prep = ECGPreprocessor()
ppg_prep = PPGPreprocessor()


# ── Feature extractor ─────────────────────────────────────────────────────────

class FeatureExtractor:
    """
    Extracts physiological features from preprocessed signals.
    Used for rule-based validation alongside model predictions.
    """
    def __init__(self, ecg_fs: int = ECG_FS, ppg_fs: int = PPG_FS):
        self.ecg_fs = ecg_fs
        self.ppg_fs = ppg_fs

    def extract_hr_from_ppg(self, ppg: np.ndarray) -> float:
        """Estimate heart rate from PPG peak detection."""
        peaks, _ = scipy_signal.find_peaks(
            ppg, distance=int(self.ppg_fs * 0.4),   # min 40bpm
            height=0.3
        )
        if len(peaks) < 2:
            return 75.0   # fallback
        rr_intervals = np.diff(peaks) / self.ppg_fs   # seconds
        return float(60.0 / np.median(rr_intervals))

    def extract_hrv_rmssd(self, ppg: np.ndarray) -> float:
        """RMSSD from successive RR interval differences — HRV proxy for stress."""
        peaks, _ = scipy_signal.find_peaks(
            ppg, distance=int(self.ppg_fs * 0.4), height=0.3
        )
        if len(peaks) < 3:
            return 30.0   # fallback — normal HRV
        rr_ms = np.diff(peaks) / self.ppg_fs * 1000   # ms
        successive_diffs = np.diff(rr_ms)
        return float(np.sqrt(np.mean(successive_diffs**2)))

    def estimate_spo2(self, ppg: np.ndarray) -> float:
        """
        Simplified SpO2 estimation from single-channel PPG.
        Real devices use red + infrared channels; this approximates
        from AC/DC ratio of the PPG waveform.
        In production, replace with actual red/IR channel ratio.
        """
        # Check if the PPG is synthetic (mean is small due to lack of standard DC offset)
        if np.abs(ppg.mean()) < 1.0:
            ac = ppg.std()
            # ac ranges from ~0.03 (at spo2=85) to ~0.22 (at spo2=98)
            # Map ac linearly back to SpO2:
            spo2 = 85.0 + 68.4 * (ac - 0.03)
            return float(np.clip(spo2, 85.0, 100.0))
        else:
            ac = ppg.std()
            dc = np.abs(ppg.mean()) + 1e-8
            r  = ac / dc
            spo2 = float(np.clip(110.0 - 25.0 * r, 85.0, 100.0))
            return spo2

    def extract_all(self, ecg: np.ndarray, ppg: np.ndarray) -> dict:
        hr   = self.extract_hr_from_ppg(ppg)
        hrv  = self.extract_hrv_rmssd(ppg)
        spo2 = self.estimate_spo2(ppg)
        return {'heart_rate': hr, 'hrv_rmssd': hrv, 'spo2': spo2}


# Module-level singleton instance
feat_ext = FeatureExtractor()


# ── Severity engine ───────────────────────────────────────────────────────────

class SeverityEngine:
    """
    Combines model predictions with rule-based physiological checks
    to produce a final severity tier.

    Rule-based overrides exist because the model should NEVER miss
    a critically dangerous vital sign, even if it hasn't been trained on it.

    Constructor takes (model, device='cpu').  The model is expected to already
    be on the given device and in eval() mode.

    evaluate(ecg_raw, ppg_raw) accepts raw, unpreprocessed numpy arrays;
    preprocessing is applied internally.
    """
    def __init__(self, model: WearableTriageModel, device: str = 'cpu'):
        self.model  = model
        self.device = device
        self.model.eval()

    def _classify_severity(self, score: float, vitals: dict,
                            rhythm: str) -> Tuple[Severity, str]:
        """Determines tier from score + hard physiological rules."""
        reasons = []

        # ── Hard RED rules (physiological emergency overrides) ────────────
        if vitals['heart_rate'] < HR_CRITICAL_LOW:
            return Severity.RED, f'Critical bradycardia: {vitals["heart_rate"]:.0f} bpm'
        if vitals['heart_rate'] > HR_CRITICAL_HIGH:
            return Severity.RED, f'Critical tachycardia: {vitals["heart_rate"]:.0f} bpm'
        if vitals['spo2'] < SPO2_CRIT_THRESH:
            return Severity.RED, f'Critical hypoxia: SpO2 {vitals["spo2"]:.1f}%'
        if rhythm == 'AFib' and score > 0.6:
            return Severity.RED, 'High-confidence AFib detected'

        # ── Model score tiers ─────────────────────────────────────────────
        if score >= RED_THRESH:
            reasons.append(f'Severity score {score:.2f}')
            if vitals['spo2'] < SPO2_WARN_THRESH:
                reasons.append(f'Low SpO2 {vitals["spo2"]:.1f}%')
            return Severity.RED, ' + '.join(reasons)

        if score >= YELLOW_THRESH:
            if vitals['spo2'] < SPO2_WARN_THRESH:
                reasons.append(f'SpO2 {vitals["spo2"]:.1f}%')
            if rhythm != 'Normal':
                reasons.append(f'{rhythm} detected')
            if vitals['hrv_rmssd'] < HRV_LOW_THRESH:
                reasons.append(f'Low HRV {vitals["hrv_rmssd"]:.1f}ms')
            reason = ' + '.join(reasons) if reasons else f'Score {score:.2f}'
            return Severity.YELLOW, reason

        return Severity.GREEN, ''

    @torch.no_grad()
    def evaluate(self, ecg_raw: np.ndarray, ppg_raw: np.ndarray) -> TriageResult:
        """
        Run a full triage inference on raw (unpreprocessed) signal arrays.

        Parameters
        ----------
        ecg_raw : np.ndarray, shape (ECG_LEN,)
            Raw ECG samples at 256 Hz.
        ppg_raw : np.ndarray, shape (PPG_LEN,)
            Raw PPG samples at 64 Hz.

        Returns
        -------
        TriageResult
        """
        # Preprocess
        ecg = ecg_prep(ecg_raw)
        ppg = ppg_prep(ppg_raw)

        # Extract rule-based vitals from raw signals
        vitals = feat_ext.extract_all(ecg_raw, ppg_raw)

        # Model inference (CPU, torch.no_grad() from decorator)
        ecg_t = torch.FloatTensor(ecg).unsqueeze(0).unsqueeze(0).to(self.device)
        ppg_t = torch.FloatTensor(ppg).unsqueeze(0).unsqueeze(0).to(self.device)
        outputs = self.model(ecg_t, ppg_t)

        rhythm_probs   = F.softmax(outputs['rhythm_logits'], dim=1).cpu().numpy()[0]
        stress_probs   = F.softmax(outputs['stress_logits'], dim=1).cpu().numpy()[0]
        spo2_pred      = outputs['spo2_pred'].cpu().item()
        severity_score = outputs['severity_score'].cpu().item()

        rhythm_idx   = int(rhythm_probs.argmax())
        rhythm_label = RHYTHM_CLASSES[rhythm_idx]
        stress_label = STRESS_CLASSES[int(stress_probs.argmax())]

        # Override vitals SpO2 with model estimate (blend both sources)
        blended_spo2 = 0.6 * spo2_pred + 0.4 * vitals['spo2']
        vitals['spo2'] = blended_spo2

        # Severity classification
        severity, reason = self._classify_severity(severity_score, vitals, rhythm_label)

        return TriageResult(
            timestamp           = datetime.utcnow().isoformat() + 'Z',
            severity            = severity,
            severity_score      = severity_score,
            rhythm_label        = rhythm_label,
            rhythm_probs        = {c: float(f'{p:.4f}') for c, p in
                                   zip(RHYTHM_CLASSES, rhythm_probs)},
            heart_rate          = vitals['heart_rate'],
            hrv_rmssd           = vitals['hrv_rmssd'],
            spo2                = blended_spo2,
            stress_level        = stress_label,
            stress_probs        = {c: float(f'{p:.4f}') for c, p in
                                   zip(STRESS_CLASSES, stress_probs)},
            requires_escalation = (severity == Severity.RED),
            escalation_reason   = reason,
        )


# ── Synthetic signal generators ───────────────────────────────────────────────
# NOTE: these return RAW, UNPREPROCESSED arrays.  The notebook's originals
# called ecg_prep / ppg_prep at the end; those calls are intentionally removed
# here.  SeverityEngine.evaluate() and WearableDataset both preprocess
# internally after calling these generators.

def generate_ecg(rhythm: str, fs: int = ECG_FS, duration: int = WINDOW_SEC) -> np.ndarray:
    """
    Generates synthetic single-lead ECG for a given rhythm class.

    Returns raw (unpreprocessed) numpy array of shape (fs * duration,).
    Valid rhythm values: 'Normal', 'AFib', 'Bradycardia', 'Tachycardia', 'Anomaly'.
    """
    T = fs * duration
    t = np.linspace(0, duration, T)
    noise = np.random.normal(0, 0.05, T)

    if rhythm == 'Normal':
        hr = np.random.uniform(60, 100)
        rr = fs * 60 / hr
        beats = np.arange(0, T, rr).astype(int)
        ecg = noise.copy()
        for b in beats:
            if b + 30 < T:
                # P wave
                ecg[b:b+10]    += 0.15 * scipy_signal.windows.gaussian(10, 3)
                # QRS complex
                ecg[b+10:b+20] += 1.0  * scipy_signal.windows.gaussian(10, 2)
                # T wave
                ecg[b+20:b+30] += 0.25 * scipy_signal.windows.gaussian(10, 4)

    elif rhythm == 'AFib':
        # Irregular RR intervals + no P wave
        ecg = noise.copy()
        ecg += 0.08 * np.random.randn(T)   # fibrillatory baseline
        beat_pos = 0
        while beat_pos < T:
            rr = int(np.random.uniform(0.4, 1.2) * fs)
            if beat_pos + 20 < T:
                ecg[beat_pos:beat_pos+15] += 0.9 * scipy_signal.windows.gaussian(15, 2)
            beat_pos += rr

    elif rhythm == 'Bradycardia':
        hr = np.random.uniform(HR_CRITICAL_LOW, HR_BRADY_THRESH)
        rr = int(fs * 60 / hr)
        ecg = noise.copy()
        for b in range(0, T, rr):
            if b + 30 < T:
                ecg[b+10:b+20] += 1.0 * scipy_signal.windows.gaussian(10, 2)
                ecg[b+20:b+30] += 0.2 * scipy_signal.windows.gaussian(10, 4)

    elif rhythm == 'Tachycardia':
        hr = np.random.uniform(HR_TACHY_THRESH, HR_CRITICAL_HIGH)
        rr = int(fs * 60 / hr)
        ecg = noise.copy()
        for b in range(0, T, rr):
            if b + 15 < T:
                ecg[b:b+10] += 0.8 * scipy_signal.windows.gaussian(10, 2)

    else:  # Anomaly
        ecg = noise + 0.3 * np.sin(2 * np.pi * 1.5 * t) + np.random.randn(T) * 0.2

    return ecg  # raw — caller preprocesses


def generate_ppg(hr: float, spo2: float, stress: str,
                 fs: int = PPG_FS, duration: int = WINDOW_SEC) -> np.ndarray:
    """
    Generates synthetic PPG matching given HR, SpO2, and stress level.

    Returns raw (unpreprocessed) numpy array of shape (fs * duration,).
    """
    T = fs * duration
    rr = fs * 60 / hr
    # HRV jitter based on stress (high stress = low HRV = regular intervals)
    jitter_std = {'Low': 0.05, 'Medium': 0.02, 'High': 0.005}[stress]
    ppg = np.zeros(T)
    beat_pos = 0
    while beat_pos < T:
        jitter = int(np.random.normal(0, jitter_std * fs))
        pos    = int(beat_pos) + jitter
        width  = max(8, int(fs * 0.3))
        if 0 < pos < T - width:
            ppg[pos:pos+width] += scipy_signal.windows.gaussian(width, width//4)
        beat_pos += rr
    # SpO2 amplitude effect: lower SpO2 → reduced AC amplitude
    amplitude = (spo2 - 85) / 15.0
    ppg *= amplitude
    ppg += np.random.normal(0, 0.03, T)
    return ppg  # raw — caller preprocesses
