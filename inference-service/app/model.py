"""
app/model.py
------------
WearableTriageModel architecture, extracted verbatim from ecg_wearable_triage.ipynb.

Constants are defined here and imported everywhere else so there is a single
source of truth for signal parameters and class labels.
"""

import torch
import torch.nn as nn
import torch.nn.functional as F

# ── Signal parameters ─────────────────────────────────────────────────────────
ECG_FS     = 256           # Hz — wearable ECG sampling rate
PPG_FS     = 64            # Hz — wearable PPG sampling rate
WINDOW_SEC = 30            # seconds per inference window
ECG_LEN    = ECG_FS * WINDOW_SEC   # 7680 samples
PPG_LEN    = PPG_FS * WINDOW_SEC   # 1920 samples

# ── Detection targets ─────────────────────────────────────────────────────────
RHYTHM_CLASSES = ['Normal', 'AFib', 'Bradycardia', 'Tachycardia', 'Anomaly']
STRESS_CLASSES = ['Low', 'Medium', 'High']

N_RHYTHM = len(RHYTHM_CLASSES)


# ── Lightweight building blocks ───────────────────────────────────────────────

class DepthwiseSeparableConv(nn.Module):
    """
    Depthwise separable convolution — same receptive field as regular Conv1D
    but 8–9× fewer parameters. Used in MobileNet; key to keeping <500K params.
    """
    def __init__(self, in_ch, out_ch, kernel_size=7, stride=1):
        super().__init__()
        pad = kernel_size // 2
        self.dw = nn.Conv1d(in_ch, in_ch, kernel_size, stride=stride,
                            padding=pad, groups=in_ch, bias=False)  # per-channel
        self.pw = nn.Conv1d(in_ch, out_ch, 1, bias=False)           # cross-channel
        self.bn = nn.BatchNorm1d(out_ch)

    def forward(self, x):
        return F.relu(self.bn(self.pw(self.dw(x))), inplace=True)


class LightResBlock(nn.Module):
    """Residual block using depthwise separable convolutions."""
    def __init__(self, channels, kernel_size=7, dropout=0.1):
        super().__init__()
        self.conv1 = DepthwiseSeparableConv(channels, channels, kernel_size)
        self.conv2 = DepthwiseSeparableConv(channels, channels, kernel_size)
        self.drop  = nn.Dropout(dropout)

    def forward(self, x):
        return x + self.drop(self.conv2(self.conv1(x)))


class SignalEncoder(nn.Module):
    """
    Shared encoder branch for ECG or PPG.
    Progressively compresses the signal into a fixed-size feature vector.
    """
    def __init__(self, in_len: int, out_dim: int = 128, dropout: float = 0.1):
        super().__init__()
        self.encoder = nn.Sequential(
            # Entry: capture local waveform shape
            nn.Conv1d(1, 16, kernel_size=7, padding=3, bias=False),
            nn.BatchNorm1d(16),
            nn.ReLU(inplace=True),
            nn.MaxPool1d(4),                    # /4

            # Stage 1
            DepthwiseSeparableConv(16, 32, stride=2),   # /2
            LightResBlock(32, dropout=dropout),

            # Stage 2
            DepthwiseSeparableConv(32, 64, stride=2),   # /2
            LightResBlock(64, dropout=dropout),

            # Stage 3
            DepthwiseSeparableConv(64, out_dim, stride=2),  # /2
            LightResBlock(out_dim, dropout=dropout),

            nn.AdaptiveAvgPool1d(16)            # fixed temporal output: 16 steps
        )

    def forward(self, x):   # x: (B, 1, T)
        return self.encoder(x)  # → (B, out_dim, 16)


# ── Full triage model ─────────────────────────────────────────────────────────

class WearableTriageModel(nn.Module):
    """
    Dual-branch ECG + PPG triage model.

    Inputs
    ------
    ecg : (B, 1, ECG_LEN)   — single-lead ECG at 256 Hz
    ppg : (B, 1, PPG_LEN)   — PPG signal at 64 Hz

    Outputs (dict)
    -------
    rhythm_logits  : (B, 5)  — Normal/AFib/Brady/Tachy/Anomaly
    stress_logits  : (B, 3)  — Low/Medium/High stress
    spo2_pred      : (B, 1)  — SpO2 estimate (%)
    severity_score : (B, 1)  — 0–1 continuous severity

    Architecture
    ------------
    ECG encoder (SignalEncoder) ─────────────────────┐
                                                      ├─ cat → (B, 256, 16)
    PPG encoder (SignalEncoder) ─────────────────────┘
                                                      ↓
                                  Fusion Conv + BiLSTM (hidden=64, 1 layer)
                                                      ↓
                                       cat(fwd, bwd) → (B, 128)
                                                      ↓
                              ┌───────────┬───────────┬───────────┐
                         Rhythm       Stress       SpO2      Severity
                          head         head        head        head
    """
    def __init__(self, dropout: float = 0.2):
        super().__init__()
        FEAT_DIM = 128

        # ── Per-signal encoders ────────────────────────────────────────────
        self.ecg_encoder = SignalEncoder(ECG_LEN, out_dim=FEAT_DIM, dropout=dropout)
        self.ppg_encoder = SignalEncoder(PPG_LEN, out_dim=FEAT_DIM, dropout=dropout)

        # ── Fusion layer — merges ECG + PPG feature maps ──────────────────
        # Input: cat(ecg_feat, ppg_feat) = (B, 256, 16)
        self.fusion = nn.Sequential(
            nn.Conv1d(FEAT_DIM * 2, FEAT_DIM, kernel_size=1, bias=False),
            nn.BatchNorm1d(FEAT_DIM),
            nn.ReLU(inplace=True)
        )

        # ── BiLSTM temporal reasoning over fused features ─────────────────
        # 1 layer — lighter than the clinical model (16 timesteps, not 32)
        self.bilstm = nn.LSTM(
            input_size    = FEAT_DIM,
            hidden_size   = 64,
            num_layers    = 1,
            batch_first   = True,
            bidirectional = True
        )
        # BiLSTM output: 64*2 = 128 dims
        self.lstm_drop = nn.Dropout(dropout)

        # ── Multi-task prediction heads ────────────────────────────────────
        def make_head(out_dim):
            return nn.Sequential(
                nn.Linear(128, 64),
                nn.ReLU(inplace=True),
                nn.Dropout(dropout),
                nn.Linear(64, out_dim)
            )

        self.rhythm_head   = make_head(N_RHYTHM)  # 5 rhythm classes
        self.stress_head   = make_head(3)          # 3 stress levels
        self.spo2_head     = make_head(1)          # SpO2 regression
        self.severity_head = make_head(1)          # severity score 0–1

    def forward(self, ecg, ppg):
        # ── Encode each signal independently ──────────────────────────────
        ecg_feat = self.ecg_encoder(ecg)      # (B, 128, 16)
        ppg_feat = self.ppg_encoder(ppg)      # (B, 128, 16)

        # ── Fuse: concatenate along channel dim, then compress ─────────────
        fused = torch.cat([ecg_feat, ppg_feat], dim=1)  # (B, 256, 16)
        fused = self.fusion(fused)                       # (B, 128, 16)

        # ── BiLSTM: (B, C, T) → (B, T, C) → LSTM → context ───────────────
        fused = fused.permute(0, 2, 1)                  # (B, 16, 128)
        _, (hn, _) = self.bilstm(fused)
        context = torch.cat([hn[0], hn[1]], dim=1)      # (B, 128)
        context = self.lstm_drop(context)

        # ── Multi-task outputs ─────────────────────────────────────────────
        return {
            'rhythm_logits':  self.rhythm_head(context),          # (B, 5)
            'stress_logits':  self.stress_head(context),          # (B, 3)
            'spo2_pred':      torch.sigmoid(self.spo2_head(context)) * 15 + 85,  # 85–100%
            'severity_score': torch.sigmoid(self.severity_head(context)),  # 0–1
        }
