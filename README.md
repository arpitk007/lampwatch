# Uptime Monitor

Checks websites on a schedule, records response times, and serves public status pages.

## Run locally

```bash
docker compose up -d --wait               # MySQL on 3307, Redis on 6381
cd backend && ./mvnw spring-boot:run      # API on http://localhost:8090
cd frontend && npm install && npx ng serve --port 4300   # UI on http://localhost:4300
```

The Angular dev server proxies `/api` to the backend (`frontend/proxy.conf.json`), so there is no CORS setup.

## Frontend

Angular 22 with standalone components, signals and typed reactive forms.

- **Monitors**: every monitor with its status, latest response time and a heartbeat strip of its last 30 checks. Refreshes every 15 seconds and pauses while the tab is hidden.
- **Monitor detail**: the last 90 checks, pass rate, average response, and a history table. The strip is keyboard accessible (arrow keys read each check).
- **Add and edit**: validation shown next to each field, including errors returned by the API.

Status is never shown by color alone: badges pair a shape with a word, and failed checks are full-height striped bars.

## Accounts and security

- Sign-up and sign-in are JSON endpoints backed by Spring Security, with bcrypt password hashes.
- Sessions are stored in Redis (Spring Session), so any app instance can serve any user. The cookie is `HttpOnly` and `SameSite=Lax`, and it gets a new id at sign-in to prevent session fixation.
- CSRF uses Spring's SPA mode: `GET /api/auth/me` sets an `XSRF-TOKEN` cookie, and Angular sends it back as `X-XSRF-TOKEN` on every write.
- Failed sign-ins are counted in Redis per email (5) and per IP (30) for 15 minutes. After that, sign-in returns `429` with `Retry-After`.
- Wrong password and unknown email get the same answer, so sign-in doesn't reveal which emails have accounts.
- Every monitor query is scoped to its owner. Another user's monitor returns `404`, the same as a missing one.
- Each account can have up to 20 monitors (`uptime.max-monitors-per-user`).

## API

All `/api/monitors` endpoints need a signed-in session.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/signup` | Create an account and sign in |
| POST | `/api/auth/login` | Sign in |
| POST | `/api/auth/logout` | Sign out |
| GET | `/api/auth/me` | The signed-in user, or `401` |
| POST | `/api/monitors` | Create a monitor |
| GET | `/api/monitors` | List monitors |
| GET | `/api/monitors/{id}` | Get one monitor |
| PUT | `/api/monitors/{id}` | Update a monitor |
| POST | `/api/monitors/{id}/pause` | Pause checks |
| POST | `/api/monitors/{id}/resume` | Resume checks now |
| DELETE | `/api/monitors/{id}` | Delete a monitor and its results |
| GET | `/api/monitors/{id}/checks?limit=50` | Latest check results, newest first |
| GET | `/api/monitors/heartbeats?limit=30` | Latest results for every monitor in one query (LATERAL join), oldest first |

## How checks run

```
 scheduler ──claim due rows──▶ MySQL (FOR UPDATE SKIP LOCKED, lease next_check_at)
     │
     └──XADD──▶ Redis Stream "checks" ──consumer group "checkers"──▶ workers (N per instance)
                       │                                               │
                       │  unacked > 60 s                               ├─ HttpChecker / KeywordChecker
                       └──▶ reclaimer ──after 3 deliveries──▶ "checks:dead"
                                                                       └─ INSERT IGNORE result (job_id unique) + atomic status UPDATE, then XACK
```

1. Every 5 seconds the scheduler claims due monitors with `SELECT … FOR UPDATE SKIP LOCKED` and moves each one's `next_check_at` forward in the same transaction (a lease), so several instances never claim the same monitor.
2. It puts one message per monitor on the `checks` Redis Stream. It never runs checks itself, so a slow website can't delay scheduling.
3. Workers in the `checkers` consumer group read one message at a time. Redis gives each message to exactly one worker, and it stays pending until the worker acknowledges it.
4. The worker runs the `Checker` for the monitor's type, stores the result and updates the status in one transaction, then acknowledges. A monitor turns `DOWN` after 3 failures in a row and `UP` after one success.
5. Delivery is at-least-once. Each result stores the stream message id in a unique `job_id` column and is written with `INSERT IGNORE`, so a redelivered message is recorded once.
6. If a worker dies mid-check, its message stays pending. The reclaimer claims messages idle for over 60 seconds and runs them again. After 3 deliveries a message goes to the `checks:dead` stream with the reason, so one bad message can't loop forever.
7. Messages older than 5 minutes are dropped rather than checked late; the next scheduler tick sends a fresh one.

`uptime.scheduler-enabled` and `uptime.worker-enabled` let an instance run only the scheduler or only workers, so the two can be scaled separately.

## Tests

```bash
cd backend && ./mvnw test                  # Testcontainers, so Docker must be running
cd frontend && npx ng test --watch=false   # Vitest
```
