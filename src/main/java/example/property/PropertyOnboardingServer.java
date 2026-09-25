package example.property;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class PropertyOnboardingServer {
    private final PropertyOnboarding service;
    public PropertyOnboardingServer(PropertyOnboarding service) { this.service = service; }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/onboard", this::onboard);
        server.start();
        System.out.println("Property onboarding listening on http://localhost:" + port);
    }

    @SuppressWarnings("unchecked")
    private void onboard(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) { reply(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        try {
            Object parsed = Json.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> body)) throw new IllegalArgumentException("JSON object required");
            PropertyOnboarding.Result result = service.onboard(PropertyOnboarding.Request.from((Map<String, Object>) body));
            reply(exchange, 201, result.json());
        } catch (IllegalArgumentException error) {
            reply(exchange, 400, Map.of("error", error.getMessage()));
        } catch (InfraiClient.InfraiException error) {
            int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
            reply(exchange, status, Map.of("error", error.code(), "message", error.getMessage()));
        } catch (Exception error) {
            reply(exchange, 502, Map.of("error", "upstream_request_failed"));
        }
    }

    private static void reply(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
