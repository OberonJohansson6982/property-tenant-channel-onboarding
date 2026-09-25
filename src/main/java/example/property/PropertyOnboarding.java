package example.property;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PropertyOnboarding {
    public interface InfraiGateway {
        String createUser(Request request) throws Exception;
        boolean emailSuppressed(String email) throws Exception;
        boolean smsSuppressed(String phone) throws Exception;
        String sendEmail(String email, String name, String idempotencyKey) throws Exception;
        String sendSms(String phone, String name, String idempotencyKey) throws Exception;
    }

    public record Request(String requestId, String signupChannel, String email, String phone, String password,
                          String name, Map<String, Object> maintenanceRequest,
                          Map<String, Object> tenantDocument, Map<String, Object> inspectionReminder) {
        static Request from(Map<String, Object> body) {
            return new Request(text(body, "request_id"), text(body, "signup_channel"), text(body, "email"),
                    text(body, "phone"), text(body, "password"), text(body, "name"), map(body, "maintenance_request"),
                    map(body, "tenant_document"), map(body, "inspection_reminder"));
        }
        void validate() {
            if (!signupChannel.equals("email") && !signupChannel.equals("sms")) throw new IllegalArgumentException("signup_channel must be email or sms");
            if (requestId.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank() || name.isBlank()) throw new IllegalArgumentException("identity fields are required");
            if (maintenanceRequest.isEmpty() || tenantDocument.isEmpty() || inspectionReminder.isEmpty()) throw new IllegalArgumentException("property records are required");
        }
        private static String text(Map<String, Object> source, String key) { return String.valueOf(source.getOrDefault(key, "")); }
        @SuppressWarnings("unchecked") private static Map<String, Object> map(Map<String, Object> source, String key) { return source.get(key) instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of(); }
    }

    public record Result(String userId, String deliveryChannel, String messageId, String decision) {
        Map<String, Object> json() { return Map.of("user_id", userId, "delivery_channel", deliveryChannel, "message_id", messageId, "decision", decision); }
    }

    private final InfraiGateway infrai;
    public PropertyOnboarding(InfraiGateway infrai) { this.infrai = infrai; }

    public Result onboard(Request input) throws Exception {
        input.validate();
        String userId = infrai.createUser(input);
        for (String channel : preference(input.signupChannel())) {
            if (channel.equals("email") && !infrai.emailSuppressed(input.email()))
                return new Result(userId, "email", infrai.sendEmail(input.email(), input.name(), input.requestId() + "-welcome-email"), "welcome_sent");
            if (channel.equals("sms") && !infrai.smsSuppressed(input.phone()))
                return new Result(userId, "sms", infrai.sendSms(input.phone(), input.name(), input.requestId() + "-welcome-sms"), "welcome_sent");
        }
        return new Result(userId, "none", "", "all_recipients_suppressed");
    }

    private static String[] preference(String signupChannel) { return signupChannel.equals("email") ? new String[]{"email", "sms"} : new String[]{"sms", "email"}; }

    static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
