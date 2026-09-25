package example.property;

import java.util.Map;

public final class PropertyOnboardingTest {
    public static void main(String[] args) throws Exception {
        FakeGateway gateway = new FakeGateway();
        PropertyOnboarding service = new PropertyOnboarding(gateway);
        PropertyOnboarding.Request input = new PropertyOnboarding.Request(
                "signup-1042", "email", "tenant@example.com", "+15551234567", "correct-horse", "Mina",
                Map.of("id", "MR-18", "summary", "Leaking kitchen tap"),
                Map.of("id", "DOC-3", "name", "move-in-condition.pdf"),
                Map.of("id", "INSP-4", "due_date", "2026-10-03"));

        PropertyOnboarding.Result result = service.onboard(input);
        check(result.deliveryChannel().equals("sms"), "suppressed signup email must fall back to SMS");
        check(result.userId().equals("usr_1042"), "account id must cross the handoff");
        check(gateway.emailSends == 0 && gateway.smsSends == 1, "exactly one welcome must be sent");
        check(gateway.created == input, "the same domain input must reach account creation");
        System.out.println("PASS email suppression selects SMS and preserves the property onboarding record");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class FakeGateway implements PropertyOnboarding.InfraiGateway {
        int emailSends;
        int smsSends;
        PropertyOnboarding.Request created;
        public String createUser(PropertyOnboarding.Request request) { created = request; return "usr_1042"; }
        public boolean emailSuppressed(String email) { return true; }
        public boolean smsSuppressed(String phone) { return false; }
        public String sendEmail(String email, String name, String key) { emailSends++; return "email_1"; }
        public String sendSms(String phone, String name, String key) { smsSends++; return "sms_1"; }
    }
}
