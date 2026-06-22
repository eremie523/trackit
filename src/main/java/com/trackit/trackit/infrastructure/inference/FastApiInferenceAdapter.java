package com.trackit.trackit.infrastructure.inference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.trackit.trackit.application.ports.inference.TriageInferencePort;
import com.trackit.trackit.core.domains.entities.ecgtriage.TriageResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Infrastructure adapter that implements {@link TriageInferencePort} by calling
 * the Python FastAPI inference microservice over HTTP.
 *
 * <p>Uses {@link java.net.http.HttpClient} (built into Java 11+, no extra
 * library) and Jackson (already declared in {@code pom.xml}) for JSON
 * serialisation and deserialisation.</p>
 *
 * <h2>Error handling</h2>
 * <p>All failure modes are caught here and wrapped in {@link InferenceException}
 * with a descriptive message. Nothing leaks past this adapter:</p>
 * <ul>
 *   <li>Network / timeout failures → {@code InferenceException} with cause</li>
 *   <li>Non-200 HTTP response → {@code InferenceException} with status + body</li>
 *   <li>JSON parse failures → {@code InferenceException} with cause</li>
 * </ul>
 *
 * <h2>Request body</h2>
 * <p>The {@code "ppg"} key is omitted entirely (not sent as JSON {@code null})
 * when {@code ppgWindow} is {@code null}, to signal ECG-only mode to the
 * Python service cleanly.</p>
 *
 * <h2>Timeouts</h2>
 * <ul>
 *   <li>Connect timeout: 3 s</li>
 *   <li>Request timeout: 5 s (per call)</li>
 * </ul>
 */
public class FastApiInferenceAdapter implements TriageInferencePort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT  = Duration.ofSeconds(5);

    private final String inferUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates an adapter targeting the given Python service base URL.
     *
     * @param baseUrl Base URL of the FastAPI service, e.g. {@code http://localhost:8001}.
     *                Must not have a trailing slash.
     */
    public FastApiInferenceAdapter(String baseUrl) {
        this.inferUrl = baseUrl.stripTrailing() + "/infer";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a JSON body of the form:</p>
     * <pre>
     * {"ecg": [0.12, ...]}                     // ECG-only
     * {"ecg": [0.12, ...], "ppg": [0.45, ...]} // dual-modality
     * </pre>
     * <p>The {@code "ppg"} key is omitted when {@code ppgWindow} is {@code null}.
     * The {@code "ecg"} key is always present.</p>
     *
     * @throws IllegalArgumentException if both arrays are {@code null}.
     * @throws InferenceException       on any network, HTTP, or parse error.
     */
    @Override
    public TriageResult infer(double[] ecgWindow, double[] ppgWindow) throws InferenceException {
        if (ecgWindow == null && ppgWindow == null) {
            throw new IllegalArgumentException(
                    "TriageInferencePort.infer(): both ecgWindow and ppgWindow are null");
        }

        String requestBody = buildRequestBody(ecgWindow, ppgWindow);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(inferUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new InferenceException("Invalid inference service URL: " + inferUrl, e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InferenceException(
                    "Network error calling inference service at " + inferUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InferenceException(
                    "Inference call was interrupted for URL " + inferUrl, e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new InferenceException(
                    "Inference service returned HTTP " + response.statusCode()
                    + " for " + inferUrl + ". Body: " + truncate(response.body(), 300));
        }

        try {
            return objectMapper.readValue(response.body(), TriageResult.class);
        } catch (Exception e) {
            throw new InferenceException(
                    "Failed to parse inference response from " + inferUrl
                    + ": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the JSON request body. The {@code "ppg"} key is omitted entirely
     * (not set to JSON {@code null}) when {@code ppgWindow} is {@code null}.
     */
    private String buildRequestBody(double[] ecgWindow, double[] ppgWindow) throws InferenceException {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            if (ecgWindow != null) {
                ArrayNode ecgNode = objectMapper.createArrayNode();
                for (double v : ecgWindow) ecgNode.add(v);
                root.set("ecg", ecgNode);
            }

            if (ppgWindow != null) {
                ArrayNode ppgNode = objectMapper.createArrayNode();
                for (double v : ppgWindow) ppgNode.add(v);
                root.set("ppg", ppgNode);
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new InferenceException("Failed to serialize inference request: " + e.getMessage(), e);
        }
    }

    /** Truncates a string to at most {@code maxLen} characters for log messages. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
