package com.trackit.trackit.core.domains.entities.ecgtriage;

/**
 * Represents a single raw sample arriving from a wearable device over the
 * WebSocket stream.
 *
 * Pure data record — no logic, no framework dependencies.
 *
 * @param channel     Which signal this sample belongs to (ECG or PPG).
 * @param value       The raw ADC / normalised amplitude value.
 * @param sampleIndex Monotonically increasing index within the stream for this
 *                    channel, used for ordering and gap detection.
 */
public record SignalSample(SignalChannel channel, double value, long sampleIndex) {}
