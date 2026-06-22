package com.trackit.trackit.core.domains.entities.ecgtriage;

/**
 * Identifies which physiological signal channel a sample belongs to.
 *
 * ECG — Electrocardiogram, sampled at 256 Hz.
 * PPG — Photoplethysmogram, sampled at 64 Hz.
 *
 * Pure domain enum — zero framework dependencies.
 */
public enum SignalChannel {
    ECG,
    PPG
}
