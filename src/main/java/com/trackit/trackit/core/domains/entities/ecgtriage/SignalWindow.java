package com.trackit.trackit.core.domains.entities.ecgtriage;

import java.util.ArrayDeque;

/**
 * Sliding-window buffer for a single physiological signal channel.
 *
 * <p>Maintains a fixed-length rolling window of {@code double} samples. When
 * the buffer is at capacity and a new sample arrives, the oldest sample is
 * evicted (FIFO sliding window) — the buffer is never structurally reset.</p>
 *
 * <p><strong>Not thread-safe by design.</strong> The caller ({@link MonitoringSession})
 * is responsible for serialising access. In the Jakarta WebSocket threading
 * model, {@code @OnMessage} is already invoked on one thread per session, so no
 * additional synchronisation is needed.</p>
 */
public class SignalWindow {

    private final int targetLength;
    private final ArrayDeque<Double> buffer;

    /**
     * Creates a new rolling buffer.
     *
     * @param samplingRateHz Samples per second for this channel (e.g. 256 for ECG, 64 for PPG).
     * @param windowSeconds  Duration of the window in seconds (e.g. 30).
     */
    public SignalWindow(int samplingRateHz, int windowSeconds) {
        this.targetLength = samplingRateHz * windowSeconds;
        this.buffer = new ArrayDeque<>(this.targetLength + 1);
    }

    /**
     * Appends a new sample to the window. If the buffer has reached
     * {@code targetLength} samples, the oldest sample (head) is evicted first,
     * preserving a sliding-window semantics rather than a circular reset.
     *
     * @param value The raw signal amplitude to append.
     */
    public void add(double value) {
        if (buffer.size() >= targetLength) {
            buffer.pollFirst(); // evict oldest — sliding window, not a reset
        }
        buffer.addLast(value);
    }

    /**
     * Returns {@code true} once the buffer has accumulated exactly
     * {@code targetLength} samples, i.e., it represents a complete window.
     */
    public boolean isFull() {
        return buffer.size() == targetLength;
    }

    /**
     * Returns a snapshot of the current buffer contents as a primitive array,
     * ordered oldest-sample-first. Safe to call even when the window is not yet
     * full — callers should check {@link #isFull()} if a complete window is
     * required before acting on the result.
     *
     * @return A new {@code double[]} containing all buffered samples.
     */
    public double[] toArray() {
        double[] result = new double[buffer.size()];
        int i = 0;
        for (double v : buffer) {
            result[i++] = v;
        }
        return result;
    }

    /**
     * Returns the configured window capacity (samplingRateHz * windowSeconds).
     */
    public int getTargetLength() {
        return targetLength;
    }

    /**
     * Returns the number of samples currently held in the buffer.
     */
    public int size() {
        return buffer.size();
    }

    // -------------------------------------------------------------------------
    // Self-test — run directly to verify sliding-window semantics
    // -------------------------------------------------------------------------

    /**
     * Manual self-test confirming two invariants:
     * <ol>
     *   <li>After exactly {@code targetLength} adds, {@link #isFull()} is {@code true}.</li>
     *   <li>Adding one more sample evicts index 0 (the oldest), not a structural reset —
     *       the window still contains {@code targetLength} elements and the first element
     *       is now what was previously at index 1.</li>
     * </ol>
     *
     * <p>Expected output (for a 4 Hz × 2 s = 8-sample window used in the test):</p>
     * <pre>
     * [PASS] isFull() is true after exactly 8 adds
     * [PASS] size is still 8 after one more add (no reset)
     * [PASS] first element is 1.0 (index-0 was evicted, not a structural reset)
     * All SignalWindow self-tests passed.
     * </pre>
     */
    public static void main(String[] args) {
        final int RATE_HZ = 4;
        final int WINDOW_S = 2;
        final int TARGET = RATE_HZ * WINDOW_S; // 8

        SignalWindow win = new SignalWindow(RATE_HZ, WINDOW_S);

        // Fill exactly to targetLength
        for (int i = 0; i < TARGET; i++) {
            win.add((double) i); // values 0.0 .. 7.0
        }

        assertTest("isFull() is true after exactly " + TARGET + " adds", win.isFull());
        assertTest("size is " + TARGET + " after filling", win.size() == TARGET);

        // The current buffer: [0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0]
        // Add one more sample (value = 8.0); oldest (0.0) should be evicted
        win.add(8.0);

        assertTest("size is still " + TARGET + " after one more add (no structural reset)", win.size() == TARGET);

        double[] snapshot = win.toArray();
        // After eviction: [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
        assertTest("first element is 1.0 (index-0 was evicted, not a structural reset)", snapshot[0] == 1.0);
        assertTest("last element is 8.0 (newest sample appended at tail)", snapshot[TARGET - 1] == 8.0);

        System.out.println("All SignalWindow self-tests passed.");
    }

    private static void assertTest(String description, boolean condition) {
        System.out.println((condition ? "[PASS] " : "[FAIL] ") + description);
        if (!condition) {
            throw new AssertionError("Self-test failed: " + description);
        }
    }
}
