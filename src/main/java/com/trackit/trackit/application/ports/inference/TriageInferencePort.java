package com.trackit.trackit.application.ports.inference;

import com.trackit.trackit.core.domains.entities.ecgtriage.TriageResult;

/**
 * Hexagonal boundary port: the application's view of the ML inference service.
 *
 * <p>The domain depends on <em>this interface</em>, never on a concrete adapter.
 * The {@code FastApiInferenceAdapter} in the infrastructure layer implements it
 * today; a TensorFlow Serving adapter, an ONNX Runtime adapter, or a test double
 * can be substituted without touching any application or domain code.</p>
 *
 * <p><strong>Intentional constraint:</strong> this interface must have zero
 * dependency on {@code java.net.http}, Jackson, Python-service-specific DTOs,
 * or anything else that is an implementation detail of a particular inference
 * backend. It operates solely on primitive arrays and domain types.</p>
 *
 * @see com.trackit.trackit.infrastructure.inference.FastApiInferenceAdapter
 */
public interface TriageInferencePort {

    /**
     * Runs inference on the supplied signal windows and returns a triage result.
     *
     * <p>Either {@code ecgWindow} or {@code ppgWindow} may be {@code null} to
     * support single-modality devices, but <strong>both cannot be null
     * simultaneously</strong>.</p>
     *
     * @param ecgWindow Ordered ECG samples (oldest first), or {@code null} if
     *                  ECG data is unavailable for this inference call.
     * @param ppgWindow Ordered PPG samples (oldest first), or {@code null} if
     *                  PPG data is unavailable (ECG-only device or window not
     *                  yet full).
     * @return The model's triage assessment for the supplied window.
     * @throws IllegalArgumentException if both arrays are {@code null}.
     * @throws InferenceException       if the underlying backend call fails for
     *                                  any reason (network error, non-200
     *                                  response, parse failure, timeout, etc.).
     */
    TriageResult infer(double[] ecgWindow, double[] ppgWindow) throws InferenceException;

    // -------------------------------------------------------------------------
    // Checked exception — keeps error handling explicit at the hexagonal boundary
    // -------------------------------------------------------------------------

    /**
     * Signals that a call to the inference backend failed. Checked so that
     * callers ({@link com.trackit.trackit.application.usecase.MonitoringSession})
     * are forced to handle or propagate the failure explicitly rather than letting
     * runtime exceptions silently crash a WebSocket session.
     */
    class InferenceException extends Exception {

        /**
         * Constructs an exception with a descriptive human-readable message.
         *
         * @param message Description of what went wrong (e.g. HTTP status, URL).
         */
        public InferenceException(String message) {
            super(message);
        }

        /**
         * Constructs an exception wrapping a lower-level cause.
         *
         * @param message Description of what went wrong.
         * @param cause   The underlying {@code IOException}, Jackson exception, etc.
         */
        public InferenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
