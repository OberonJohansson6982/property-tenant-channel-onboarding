package example.property;

public final class PropertyOnboardingApplication {
    public static void main(String[] args) throws Exception {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        new PropertyOnboardingServer(new PropertyOnboarding(new InfraiClient(config))).start(config.port());
    }
}
