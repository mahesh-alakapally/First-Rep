<<<<<<< HEAD
# FirstRep

FirstRep is a small evidence platform for early-career candidates. A student enrols in a company project, submits work, and a person reviews it against a rubric. A certificate records that review. It is not a job offer and not a rank produced by a model.

The local stack needs no Stripe account and no OpenAI key. Billing is a clearly marked simulator. Assessments use a deterministic mock provider.

## Start

From this directory:

```bash
docker compose up --build
```

The app is at [http://localhost:5173](http://localhost:5173). The same origin proxies `/api` and `/actuator` to the API, so the browser cookies stay first-party.

Leave that command running. `docker compose down` stops it.

Other local addresses:

| Service | URL |
| --- | --- |
| Web app | http://localhost:5173 |
| API health | http://localhost:8080/actuator/health |
| API via the web app | http://localhost:5173/actuator/health |
| Worker health | http://localhost:8081/actuator/health |
| Mailpit (captured mail) | http://localhost:8025 |
| MinIO console | http://localhost:9001 |
| MinIO S3 API | http://localhost:9000 |
| RabbitMQ management | http://localhost:15672 |

RabbitMQ login is `firstrep` / `firstrep`. MinIO login is `firstrep` / `firstrep-secret`. MySQL is `firstrep` / `firstrep` on port 3306, database `firstrep`.

## Development accounts

These accounts exist only after the local seed runs. They are not for a public environment. Every one uses the password `Demo-FirstRep-2026`.

| Email | Role |
| --- | --- |
| student@firstrep.local | Student |
| orgadmin@firstrep.local | Organisation admin at North Harbour Systems |
| recruiter@firstrep.local | Recruiter at North Harbour Systems |
| reviewer@firstrep.local | Reviewer at North Harbour Systems |
| admin@firstrep.local | Platform admin |
| lakeadmin@firstrep.local | Organisation admin at Lakeworks Studio, for isolation checks |

North Harbour Systems has three published projects (Shift handover API, Berth occupancy notes, and the recruitment-linked Support note triage) and one project waiting for moderation (Tide table cleaner). Lakeworks Studio has Inventory count sheet.

Public registration creates a student and sends a verification mail to Mailpit. In the local profile the API also returns the verification link in the response.

## What the seed and migrations do

Flyway runs on the API at startup (`ddl-auto` is `validate`, so Hibernate does not create tables). `backend/src/main/resources/db/migration` holds the schema and reference data: roles, permissions, the two premium plans (€19 a month and €199 a year), and the skill list.

The worker uses the same jar with the `worker` profile. It does not run Flyway and it does not seed. It consumes `assessment.jobs`.

## Tests

Backend unit and journey tests, from `backend/`:

```bash
DOCKER_API_VERSION=1.44 mvn test
```

`DOCKER_API_VERSION=1.44` is required on Docker Engine 29, whose minimum API is newer than the Testcontainers client default. Journey tests start MySQL and RabbitMQ with Testcontainers. Rules tests do not need Docker.

Frontend, from `frontend/`:

```bash
npm test
npm run build
```

`npm run dev` in `frontend/` serves Vite on port 5173 and proxies `/api` to `http://127.0.0.1:8080`. Start MySQL, RabbitMQ, MinIO, and Mailpit first, then the API with the `local` profile.

## Configuration

Copy `.env.example` if you want to override the process outside Compose. Compose already sets local defaults. Important variables:

| Variable | Local default | Purpose |
| --- | --- | --- |
| `FIRSTREP_BILLING_MODE` | `simulator` | `simulator` or `stripe` |
| `FIRSTREP_AI_PROVIDER` | `mock` | `mock` or `openai` |
| `JWT_SECRET` | a dev-only value in Compose | HMAC secret, at least 32 bytes |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | empty | Used only when billing mode is `stripe` |
| `OPENAI_API_KEY` | empty | Used only when the AI provider is `openai` |
| `FIRSTREP_EXPOSE_EMAIL_TOKENS` | `true` on the local profile | Puts verify and reset links in API responses |
| `STORAGE_MODE` | `s3` | `s3` for MinIO, `memory` in tests |

Stripe checkout does not grant premium from the browser return. A signed webhook, or the simulator confirm action, writes the same idempotent payment event. The webhook route is `POST /api/v1/webhooks/stripe`.

The mock assessor scores from the overlap between criterion titles and the submitted text. It does not call a network. A validator rejects scores outside the rubric, unknown criterion ids, and empty feedback.

## Theme

The theme control in the header cycles System, Light, and Dark. The choice is stored in `localStorage` under `firstrep-theme`. System is the default and follows the operating system preference.

## Limits of this build

- The project editor shows the first deliverable, the first criterion, and the first skills question. Saving keeps the remaining rows that were already stored.
- Skills-round short answers are stored and are not auto-scored. Multiple choice is scored.
- A certificate is issued when a reviewer approves. It does not wait for the skills round.
- The official `minio/minio` image is no longer on Docker Hub. Compose uses `chainguard/minio`.
- The Cursor agent CLI is installed, but this machine is not logged in and `CURSOR_API_KEY` is unset, so the CLI could not edit or run the app. See `IMPLEMENTATION_STATUS.md`.
=======
# First-Rep
I created a platform where students can complete thier company projects to gain practical skills and submit thier project and get certificate for their completed project and the AI system send their CV to the recrutment team to for job . With this platform students gain realtime experience and companies get skilled employes.
>>>>>>> 952517a86cb9062fb1d4d6a6d13dfddb46c12dc4
