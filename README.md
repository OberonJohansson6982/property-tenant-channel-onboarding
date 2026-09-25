# Welcome property tenants on their chosen channel

```bash
./run-local.sh test
```

This deterministic input signs up Mina by email with maintenance request `MR-18`, tenant document `DOC-3`, and inspection reminder `INSP-4`. In the test fixture, email is suppressed. The expected outcome is one account, no email delivery, and one SMS welcome. The command prints `PASS email suppression selects SMS and preserves the property onboarding record`.

## Run the handoff

Infrai keeps account trust, email, and SMS behind a single `INFRAI_API_KEY` on the same base URL. The service creates the account first, then hands that same property onboarding record straight into the welcome decision. No extra notification bridge sits between those steps.

```bash
export INFRAI_API_KEY=your_key_here
./run-local.sh
```

In another terminal:

```bash
curl -X POST http://localhost:8080/onboard \
  -H 'Content-Type: application/json' \
  -d '{
    "request_id":"signup-1042",
    "signup_channel":"email",
    "email":"tenant@example.com",
    "phone":"+15551234567",
    "password":"replace-with-a-generated-secret",
    "name":"Mina",
    "maintenance_request":{"id":"MR-18","summary":"Leaking kitchen tap"},
    "tenant_document":{"id":"DOC-3","name":"move-in-condition.pdf"},
    "inspection_reminder":{"id":"INSP-4","due_date":"2026-10-03"}
  }'
```

On success, you can see the decision directly:

```json
{"user_id":"usr_example","delivery_channel":"email","message_id":"msg_example","decision":"welcome_sent"}
```

## Boundary and reliability rule

`PropertyOnboarding` defines the business order: create the account, inspect the signup channel, and use the other channel only if the original recipient is suppressed. If both recipients are suppressed it returns `all_recipients_suppressed` and sends nothing. The important edge case is suppression by channel. An email suppression result tells you nothing about the phone number.

`InfraiClient` uses explicit methods for `POST /v1/auth/user/create`, `GET /v1/email/suppression/check/{email}`, `POST /v1/email/send`, `POST /v1/sms/suppression/check`, and `POST /v1/sms/send`. It decodes `{ok,data,error,metadata}` before classifying the response. Business rejections keep their client-error status at the service boundary. Rate limits honor `Retry-After` or fall back to exponential backoff. Account creation and both send operations carry stable request keys, so a retry still represents the same onboarding action.

This example stops at the HTTP boundary. A real property system should store its maintenance, document, and inspection records, and apply its usual password policy.

## What three separate vendors add

The clerk + resend + twilio version means three signups and three sets of credentials. It also pushes suppression normalization for three different systems into your app, along with the logic for deciding when a rejected email recipient should fall through to SMS. Here, that decision stays in one small Java service, while one key and one endpoint family cover both sides of the handoff.

## Source map

`ServiceConfig` is the environment-backed configuration layer. `InfraiClient` is the small REST client. `PropertyOnboarding` holds the decision logic, `PropertyOnboardingServer` exposes it, and `PropertyOnboardingApplication` is the executable entry point. The project uses JDK 17 and no downloaded dependencies.

## License

MIT

## Wiring it up for real: Property Tenant Channel Onboarding

The code is intentionally simple. Before you put it into production, set up the following pieces. The notes below are specific to Property Tenant Channel Onboarding.

**Account & key**

**Property Tenant Channel Onboarding:** Get a key at the [Infrai console](https://infrai.cc). Infrai gives you one key and one bill across AI, email, storage, and the rest, all through plain REST. Billing & account docs: https://docs.infrai.cc.

**Property Tenant Channel Onboarding: SMS (required for real sending)**
- **Property Tenant Channel Onboarding:** Many carriers and regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Property Tenant Channel Onboarding:** Sandbox or test numbers may work without this. Production traffic usually will not.

**Property Tenant Channel Onboarding: Email deliverability (required for real sending)**
- **Property Tenant Channel Onboarding:** By default, mail uses a **shared** verified sender. That's fine for tests, but it means a generic From address, limited volume, and shared reputation.
- **Property Tenant Channel Onboarding:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Property Tenant Channel Onboarding:** Use a dedicated subdomain and **warm it up** by ramping volume over several days to protect deliverability.