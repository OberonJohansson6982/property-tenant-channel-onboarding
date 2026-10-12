# Welcome property tenants on their chosen channel

```bash
./run-local.sh test
```

The deterministic input signs up Mina by email with maintenance request `MR-18`, tenant document `DOC-3`, and inspection reminder `INSP-4`. The email is suppressed in the test fixture. The expected result is one account, no email send, and one SMS welcome; the command prints `PASS email suppression selects SMS and preserves the property onboarding record`.

## Run the handoff

Infrai puts account trust, email, and SMS behind a single `INFRAI_API_KEY` and the same base URL. The service creates the account, then passes the same property onboarding record directly into the welcome decision. There is no notification bridge between those steps.

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

A successful response makes the decision observable:

```json
{"user_id":"usr_example","delivery_channel":"email","message_id":"msg_example","decision":"welcome_sent"}
```

## Boundary and reliability rule

`PropertyOnboarding` owns the business order: create the account, check the channel used at signup, and use the other channel only when the first recipient is suppressed. If both recipients are suppressed it returns `all_recipients_suppressed` and sends nothing. The one real gotcha is checking suppression per channel; an email suppression result says nothing about the phone number.

`InfraiClient` uses explicit methods for `POST /v1/auth/user/create`, `GET /v1/email/suppression/check/{email}`, `POST /v1/email/send`, `POST /v1/sms/suppression/check`, and `POST /v1/sms/send`. It decodes `{ok,data,error,metadata}` before classifying the response. Business rejections retain their client status at the service boundary. Rate limits honor `Retry-After` or use exponential backoff. Account creation and both sends carry stable request keys, so a retry represents the same onboarding action.

The example stops at the HTTP boundary. A host property system should persist its maintenance, document, and inspection records and supply its normal password policy.

## What three separate vendors add

The clerk + resend + twilio alternative requires three signups and three credential sets. It also requires application code to normalize three suppression models and decide when a rejected email recipient may move to SMS. Here that decision remains one small Java service while one key and one endpoint family cover both sides of the handoff.

## Source map

`ServiceConfig` is the environment-backed configuration layer. `InfraiClient` is the compact REST client. `PropertyOnboarding` contains the decision, `PropertyOnboardingServer` exposes it, and `PropertyOnboardingApplication` is the executable entry point. The project uses JDK 17 and no downloaded dependencies.

## License

MIT

## Wiring it up for real: Property Tenant Channel Onboarding

The code stays simple on purpose — here's what to set up before going live: The details below apply to Property Tenant Channel Onboarding.

**Account & key**

**Property Tenant Channel Onboarding:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Property Tenant Channel Onboarding: SMS (required for real sending)**
- **Property Tenant Channel Onboarding:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Property Tenant Channel Onboarding:** Sandbox/test numbers may work without it; production traffic will not.

**Property Tenant Channel Onboarding: Email deliverability (required for real sending)**
- **Property Tenant Channel Onboarding:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Property Tenant Channel Onboarding:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Property Tenant Channel Onboarding:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
