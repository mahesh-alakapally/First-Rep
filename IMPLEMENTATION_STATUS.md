# Implementation status

The stack is running from `/agent` with `docker compose up --build`.

Local URL: http://localhost:5173

API health: http://localhost:8080/actuator/health and http://localhost:5173/actuator/health

Worker health: http://localhost:8081/actuator/health

## Demo accounts

Development only. Password for every account: `Demo-FirstRep-2026`

- student@firstrep.local — student
- orgadmin@firstrep.local — organisation admin, North Harbour Systems
- recruiter@firstrep.local — recruiter, North Harbour Systems
- reviewer@firstrep.local — reviewer, North Harbour Systems
- admin@firstrep.local — platform admin
- lakeadmin@firstrep.local — organisation admin, Lakeworks Studio

## What works

- One Compose command starts MySQL 8.4, RabbitMQ, MinIO (`chainguard/minio`, because `minio/minio` is no longer on Docker Hub), Mailpit, the API, the assessment worker, and the nginx frontend.
- Flyway creates the schema. Hibernate validates it and does not generate DDL.
- The local seed creates the accounts and projects above. Tide table cleaner stays pending. The other four projects are published.
- Register, verify, login, refresh cookies, and CSRF are covered by the journey test. Access and refresh cookies are HttpOnly. Company permissions are checked from membership, not from the JWT.
- Two free enrolments succeed. The third returns 402 until the billing simulator confirm runs. The success redirect does not grant premium.
- Submit a link, asynchronous mock assessment, hidden scores until release, human approve, certificate, public verify without the email address, skills-round multiple choice, share, and revoke are covered by `JourneyIT`.
- A second student cannot read the first student's submission or share package. A concurrent burst cannot consume a third free slot.
- Light, dark, and system theme. The choice is stored as `firstrep-theme` and defaults to system.
- Each signed-in role has a persistent nav back to home, their work, and account.

## Commands that were run

| Command | Result |
| --- | --- |
| `mvn -DskipTests compile` in `backend/` | Passed |
| `DOCKER_API_VERSION=1.44 mvn -Dtest=JourneyIT,RulesTest test` | Passed. JourneyIT 2 tests, RulesTest 5 tests, 0 failures |
| `npm test` in `frontend/` | Passed. 1 theme test |
| `npx tsc --noEmit` in `frontend/` | Passed |
| `docker compose up --build -d` | Passed. All services healthy |
| `curl -fsS http://127.0.0.1:8080/actuator/health` | `status` UP, including MySQL and RabbitMQ |
| `curl -fsS http://127.0.0.1:8081/actuator/health` | `status` UP |
| `curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:5173/` | 200 |
| `curl -fsS http://127.0.0.1:5173/actuator/health` | `status` UP through the frontend proxy |
| Login `student@firstrep.local` and `admin@firstrep.local` through the proxy | Succeeded. Catalogue returned 4 published projects. Admin queue included Tide table cleaner as `PENDING_MODERATION` |
| Browser pass of http://localhost:5173 | Passed. Light and dark theme, student enrol into Shift handover API, org admin, reviewer, recruiter, platform admin moderation, and Lakeworks isolation all rendered with the role-appropriate nav |
| `agent --help`, then `agent status`, then `agent -p --trust --mode=ask "Reply with the single word pong."` | CLI is installed. Status: `Not logged in`. Print mode: `Error: Authentication required. Please run 'agent login' first, or set CURSOR_API_KEY environment variable.` |

`DOCKER_API_VERSION=1.44` is required for Testcontainers on this machine's Docker Engine 29. The full `mvn test` command is the same set as the filtered run above; those two classes are the test suite.

## Cursor agent CLI

The CLI was discovered with `agent --help` before any flag was used. It cannot implement or verify this workspace until someone runs `agent login` or sets `CURSOR_API_KEY`. No hidden flags were used. The spec at `FIRSTREP_BUILD_SPEC.md` stayed the source of the product behaviour.

## Not finished

- Playwright is not wired. A person walked the running UI: theme, student enrol, and each role's nav. The student demo account has used one free enrolment on Shift handover API.
- Stripe and OpenAI adapters are present and unused by the default configuration. They were not called.
- The project editor edits the first deliverable, the first criterion, and the first skills question, and keeps any further rows already stored.
- The assessment listener uses automatic acknowledgement. A crash after the ack and before the draft is saved will not republish that outbox row.
- There is no git remote. Nothing was pushed and no pull request was opened.
