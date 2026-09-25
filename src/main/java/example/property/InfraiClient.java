package example.property;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class InfraiClient implements PropertyOnboarding.InfraiGateway {
    private final ServiceConfig config;
    private final HttpClient http;

    public InfraiClient(ServiceConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiClient(ServiceConfig config, HttpClient http) { this.config = config; this.http = http; }

    public String createUser(PropertyOnboarding.Request input) throws IOException, InterruptedException {
        Map<String, Object> metadata = Map.of(
                "signup_channel", input.signupChannel(),
                "phone", input.phone(),
                "maintenance_request", input.maintenanceRequest(),
                "tenant_document", input.tenantDocument(),
                "inspection_reminder", input.inspectionReminder());
        Map<String, Object> data = call("POST", "/v1/auth/user/create", Map.of(
                "email", input.email(), "password", input.password(), "name", input.name(),
                "metadata", metadata, "idempotency_key", input.requestId()), input.requestId());
        return requiredString(data, "id");
    }

    public boolean emailSuppressed(String email) throws IOException, InterruptedException {
        Map<String, Object> data = call("GET", "/v1/email/suppression/check/" + segment(email), null, null);
        return Boolean.TRUE.equals(data.get("suppressed"));
    }

    public boolean smsSuppressed(String phone) throws IOException, InterruptedException {
        Map<String, Object> data = call("POST", "/v1/sms/suppression/check", Map.of("phone", phone), null);
        return Boolean.TRUE.equals(data.get("suppressed"));
    }

    public String sendEmail(String email, String name, String key) throws IOException, InterruptedException {
        Map<String, Object> data = call("POST", "/v1/email/send", Map.of(
                "to", email, "subject", "Your property portal is ready",
                "body", "Hi " + name + ", your maintenance requests, tenant documents, and inspection reminders are ready."), key);
        return requiredString(data, "message_id");
    }

    public String sendSms(String phone, String name, String key) throws IOException, InterruptedException {
        Map<String, Object> data = call("POST", "/v1/sms/send", Map.of(
                "to", phone, "body", "Hi " + name + ", your property portal is ready.", "idempotency_key", key), key);
        return requiredString(data, "message_id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> body, String idempotencyKey)
            throws IOException, InterruptedException {
        String payload = body == null ? "" : Json.stringify(body);
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(payload));

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Object parsed;
            try { parsed = Json.parse(response.body()); }
            catch (IllegalArgumentException error) { throw new IOException("Infrai returned an unreadable response", error); }
            if (!(parsed instanceof Map<?, ?> raw)) throw new IOException("Infrai returned an invalid envelope");
            Map<String, Object> envelope = (Map<String, Object>) raw;
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
                throw new InfraiException(response.statusCode(), String.valueOf(error.getOrDefault("code", "request_rejected")), String.valueOf(error.getOrDefault("message", "Request rejected")));
            }
            if (response.statusCode() >= 500) throw new IOException("Upstream transport status " + response.statusCode());
            return envelope.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of();
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try { return Math.max(0L, Long.parseLong(value) * 1000L); }
        catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
    }

    private static String requiredString(Map<String, Object> data, String key) throws IOException {
        Object value = data.get(key);
        if (value == null || value.toString().isBlank()) throw new IOException("Response data omitted " + key);
        return value.toString();
    }

    private static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }

    public static final class InfraiException extends IOException {
        private final int status;
        private final String code;
        InfraiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }
}
