# FirstRep implementation plan

The workspace was empty: no application, no git remote, and no existing framework to preserve. This plan follows `FIRSTREP_BUILD_SPEC.md`.

## Repository state

- No prior frontend, backend, migrations, or Docker files.
- Java 21 is available. Maven and Docker were installed in this environment so the stack can be built and started.
- Cursor CLI (`agent` / `cursor-agent`) is installed. `agent --help` is the flag source. Headless runs need `agent login` or `CURSOR_API_KEY`. Neither was available when implementation started, so product code is written in this session and the CLI is retried for a later slice and a final check.

## Architecture mapping

Modular Spring Boot monolith (`backend/`) plus a second process of the same jar with the `worker` profile. React/Vite client (`frontend/`). MySQL is the system of record. RabbitMQ carries assessment jobs from a transactional outbox. MinIO holds private uploads and certificate PDFs. Mailpit captures mail. Billing defaults to a clearly marked local simulator. AI defaults to a deterministic mock provider. Stripe and OpenAI stay behind configuration and are not required for the acceptance journeys.

## Milestones

1. Compose, Flyway, health, configuration.
2. Identity, roles, company memberships, cookie session, CSRF, auth throttling.
3. Companies, projects, immutable rubrics, moderation, public catalogue.
4. Free allowance (two slots, row lock, unique pair) and premium entitlements.
5. Local billing simulator on the same idempotent payment-event path as signed Stripe webhooks.
6. Submissions, private storage, link checks that do not fetch URLs.
7. Outbox, worker, mock assessment, human review, appeals.
8. Fixed-format skills round and certificate PDF plus public verification.
9. Consent-based evidence packages and revocation.
10. Admin reports, audit, in-app notifications, themes, role navigation.
11. Automated tests and a running compose stack.

## Risks

- Guest RabbitMQ users do not work across containers, so Compose sets a dedicated broker user.
- Cookie auth must stay same-origin. The dev server and nginx both proxy `/api`.
- Schema validation (`ddl-auto=validate`) will fail closed if an entity drifts from Flyway.
- Docker image builds are the slow path; host `mvn` and `npm` are used for tests as well.
- CLI authentication may remain blocked without a browser login or API key. That does not block the product, and it is recorded in `IMPLEMENTATION_STATUS.md` rather than claimed as done.
