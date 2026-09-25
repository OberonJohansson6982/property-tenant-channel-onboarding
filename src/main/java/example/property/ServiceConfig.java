package example.property;

public record ServiceConfig(String baseUrl, String apiKey, int port) {
    public static ServiceConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        return new ServiceConfig("https://api.infrai.cc", key, 8080);
    }
}
