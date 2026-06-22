package com.trackit.trackit.core.domains.entities.ecgtriage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Mirrors exactly the JSON payload returned by the Python ML inference service.
 * Deserialized by {@code FastApiInferenceAdapter} and serialized back to the
 * WebSocket client by {@code EcgMonitorEndpoint}.
 *
 * All field names use camelCase to match the Python service's snake_case → Java
 * naming convention handled by the Jackson {@code @JsonProperty} annotations on
 * the canonical constructor.
 *
 * Pure domain record — only standard-library and Jackson annotation imports.
 * The nested {@link Severity} enum belongs here because severity is a domain
 * concept, not an infrastructure one.
 */
public record TriageResult(
        Instant timestamp,
        Severity severity,
        double severityScore,
        String rhythmLabel,
        Map<String, Double> rhythmProbs,
        double heartRate,
        double hrvRmssd,
        double spo2,
        String stressLevel,
        Map<String, Double> stressProbs,
        boolean requiresEscalation,
        String escalationReason
) {

    /**
     * Clinical severity classification returned by the inference model.
     */
    public enum Severity {
        GREEN,
        YELLOW,
        RED
    }

    /**
     * Explicit Jackson constructor so the record's compact constructor works
     * seamlessly with {@code ObjectMapper} without needing
     * {@code @JsonDeserialize(builder = ...)}.
     */
    @JsonCreator
    public TriageResult(
            @JsonProperty("timestamp")         Instant timestamp,
            @JsonProperty("severity")          Severity severity,
            @JsonProperty("severityScore")     double severityScore,
            @JsonProperty("rhythmLabel")       String rhythmLabel,
            @JsonProperty("rhythmProbs")       Map<String, Double> rhythmProbs,
            @JsonProperty("heartRate")         double heartRate,
            @JsonProperty("hrvRmssd")          double hrvRmssd,
            @JsonProperty("spo2")              double spo2,
            @JsonProperty("stressLevel")       String stressLevel,
            @JsonProperty("stressProbs")       Map<String, Double> stressProbs,
            @JsonProperty("requiresEscalation") boolean requiresEscalation,
            @JsonProperty("escalationReason")  String escalationReason
    ) {
        this.timestamp         = timestamp;
        this.severity          = severity;
        this.severityScore     = severityScore;
        this.rhythmLabel       = rhythmLabel;
        this.rhythmProbs       = rhythmProbs;
        this.heartRate         = heartRate;
        this.hrvRmssd          = hrvRmssd;
        this.spo2              = spo2;
        this.stressLevel       = stressLevel;
        this.stressProbs       = stressProbs;
        this.requiresEscalation = requiresEscalation;
        this.escalationReason  = escalationReason;
    }
}
