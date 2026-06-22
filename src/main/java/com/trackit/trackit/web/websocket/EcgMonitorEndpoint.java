package com.trackit.trackit.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.trackit.trackit.application.ports.inference.TriageInferencePort;
import com.trackit.trackit.application.usecase.MonitoringSession;
import com.trackit.trackit.core.domains.entities.ecgtriage.SignalChannel;
import com.trackit.trackit.core.domains.entities.ecgtriage.SignalSample;
import com.trackit.trackit.core.domains.entities.ecgtriage.TriageResult;
import com.trackit.trackit.infrastructure.inference.FastApiInferenceAdapter;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jakarta WebSocket server endpoint for real-time ECG/PPG triage monitoring.
 *
 * <p>Endpoint path: {@code /ws/ecg-monitor}</p>
 *
 * <h2>Protocol</h2>
 * <p><strong>Client → Server</strong> (one JSON text message per sample):</p>
 * <pre>
 * {"channel": "ecg"|"ppg", "value": &lt;number&gt;, "sampleIndex": &lt;integer&gt;}
 * </pre>
 *
 * <p><strong>Server → Client</strong> (triage result):</p>
 * <pre>
 * {"type": "triageResult", "timestamp": "...", "severity": "GREEN|YELLOW|RED", ...}
 * </pre>
 *
 * <p><strong>Server → Client</strong> (error notification):</p>
 * <pre>
 * {"type": "error", "message": "..."}
 * </pre>
 *
 * <h2>Wiring</h2>
 * <p>On open, the endpoint reads the {@code INFERENCE_SERVICE_URL} environment
 * variable (defaults to {@code http://localhost:8001}) and creates a fresh
 * {@link MonitoringSession} per connection. This keeps the endpoint thin —
 * all HTTP and JSON details stay in their respective adapters.</p>
 *
 * <h2>Threading</h2>
 * <p>Jakarta WebSocket guarantees {@code @OnMessage} is invoked on at most one
 * thread at a time per session, which satisfies {@link MonitoringSession}'s
 * not-thread-safe contract. {@link ConcurrentHashMap} is used only because the
 * map itself is accessed from multiple sessions concurrently.</p>
 */
@ServerEndpoint("/ws/ecg-monitor")
public class EcgMonitorEndpoint {

    private static final String DEFAULT_INFERENCE_URL = "http://localhost:8001";
    private static final String ENV_INFERENCE_URL     = "INFERENCE_SERVICE_URL";

    /**
     * One {@link MonitoringSession} per active WebSocket connection.
     * Keyed by the Jakarta {@link Session} so cleanup on close is O(1).
     */
    private static final ConcurrentHashMap<Session, MonitoringSession> sessions =
            new ConcurrentHashMap<>();

    /**
     * Shared {@link ObjectMapper} configured with Java-time support for
     * {@code Instant} serialisation. Thread-safe after construction.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Invoked when a new WebSocket connection is established.
     *
     * <p>Creates a {@link FastApiInferenceAdapter} targeting the configured
     * Python service URL, wires the result and error callbacks, and stores the
     * new {@link MonitoringSession} in the session map.</p>
     *
     * @param jakartaSession The newly opened Jakarta WebSocket session.
     */
    @OnOpen
    public void onOpen(Session jakartaSession) {
        String inferenceUrl = resolveInferenceUrl();
        TriageInferencePort inferenceAdapter = new FastApiInferenceAdapter(inferenceUrl);

        MonitoringSession monitoringSession = new MonitoringSession(
                inferenceAdapter,
                result   -> sendTriageResult(jakartaSession, result),
                errorMsg -> sendError(jakartaSession, errorMsg)
        );

        sessions.put(jakartaSession, monitoringSession);
        System.out.println("[EcgMonitorEndpoint] Session opened: " + jakartaSession.getId()
                + " → inference at " + inferenceUrl);
    }

    /**
     * Invoked for each incoming text message (one sample per message).
     *
     * <p>Parses the JSON message into a {@link SignalSample}, looks up the
     * session's {@link MonitoringSession}, and calls
     * {@link MonitoringSession#onSample(SignalSample)}. Parse failures are
     * caught and sent back as error messages rather than crashing the
     * connection.</p>
     *
     * @param message        Raw JSON text message from the client.
     * @param jakartaSession The session that sent the message.
     */
    @OnMessage
    public void onMessage(String message, Session jakartaSession) {
        MonitoringSession monitoringSession = sessions.get(jakartaSession);
        if (monitoringSession == null) {
            // Should not happen, but guard defensively
            sendError(jakartaSession, "No active monitoring session found for this connection.");
            return;
        }

        SignalSample sample;
        try {
            sample = parseSample(message);
        } catch (Exception e) {
            sendError(jakartaSession, "Malformed sample message: " + e.getMessage()
                    + ". Expected: {\"channel\":\"ecg\"|\"ppg\",\"value\":<number>,\"sampleIndex\":<integer>}");
            return;
        }

        monitoringSession.onSample(sample);
    }

    /**
     * Invoked when the WebSocket connection is closed normally or by the client.
     *
     * @param jakartaSession The session that was closed.
     */
    @OnClose
    public void onClose(Session jakartaSession) {
        sessions.remove(jakartaSession);
        System.out.println("[EcgMonitorEndpoint] Session closed: " + jakartaSession.getId());
    }

    /**
     * Invoked when a WebSocket protocol error occurs (distinct from application
     * errors, which are routed through the {@code onError} consumer).
     *
     * @param jakartaSession The session on which the error occurred.
     * @param throwable      The underlying error.
     */
    @OnError
    public void onError(Session jakartaSession, Throwable throwable) {
        sessions.remove(jakartaSession);
        System.err.println("[EcgMonitorEndpoint] WebSocket error on session "
                + jakartaSession.getId() + ": " + throwable.getMessage());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the raw JSON message into a {@link SignalSample}.
     *
     * @throws IllegalArgumentException if required fields are missing or invalid.
     * @throws IOException              if the JSON is malformed.
     */
    private static SignalSample parseSample(String message) throws IOException {
        JsonNode root = MAPPER.readTree(message);

        JsonNode channelNode     = root.get("channel");
        JsonNode valueNode       = root.get("value");
        JsonNode sampleIndexNode = root.get("sampleIndex");

        if (channelNode == null || valueNode == null || sampleIndexNode == null) {
            throw new IllegalArgumentException(
                    "Missing required field(s). Required: channel, value, sampleIndex.");
        }

        String channelStr = channelNode.asText().toUpperCase();
        SignalChannel channel;
        try {
            channel = SignalChannel.valueOf(channelStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown channel \"" + channelNode.asText() + "\". Valid values: ecg, ppg.");
        }

        double value       = valueNode.asDouble();
        long   sampleIndex = sampleIndexNode.asLong();

        return new SignalSample(channel, value, sampleIndex);
    }

    /**
     * Serializes a {@link TriageResult} and sends it to the client as a JSON
     * text message with an added {@code "type": "triageResult"} field.
     *
     * <p>Silently skips the send if the session is no longer open (the client
     * may have disconnected between inference firing and the result callback
     * being invoked).</p>
     */
    private static void sendTriageResult(Session jakartaSession, TriageResult result) {
        if (!jakartaSession.isOpen()) return;
        try {
            // Deserialize result to a generic ObjectNode so we can inject "type"
            ObjectNode node = (ObjectNode) MAPPER.valueToTree(result);
            node.put("type", "triageResult");
            String json = MAPPER.writeValueAsString(node);
            jakartaSession.getBasicRemote().sendText(json);
        } catch (Exception e) {
            System.err.println("[EcgMonitorEndpoint] Failed to send triage result on session "
                    + jakartaSession.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Sends an error message to the client.
     *
     * <p>Silently skips the send if the session is no longer open.</p>
     */
    private static void sendError(Session jakartaSession, String errorMessage) {
        if (!jakartaSession.isOpen()) return;
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", "error");
            node.put("message", errorMessage);
            jakartaSession.getBasicRemote().sendText(MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            System.err.println("[EcgMonitorEndpoint] Failed to send error message on session "
                    + jakartaSession.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Reads the inference service URL from the environment variable
     * {@code INFERENCE_SERVICE_URL}, falling back to {@code http://localhost:8001}.
     */
    private static String resolveInferenceUrl() {
        String envUrl = System.getenv(ENV_INFERENCE_URL);
        return (envUrl != null && !envUrl.isBlank()) ? envUrl.strip() : DEFAULT_INFERENCE_URL;
    }
}
