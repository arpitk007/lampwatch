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

## API

| Method | Path | Purpose |
|---|---|---|
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

1. Every 5 seconds the scheduler claims due monitors with `SELECT … FOR UPDATE SKIP LOCKED`, so several app instances never check the same monitor twice.
2. In the same transaction it moves each claimed monitor's `next_check_at` forward by its interval. That acts as a lease.
3. A bounded thread pool runs each check through the `Checker` for its type (`HttpChecker`, `KeywordChecker`).
4. The result is saved, and the monitor's status is updated with one atomic `UPDATE`. A monitor turns `DOWN` after 3 failures in a row and `UP` after one success.

Example:

```bash
curl -X POST localhost:8090/api/monitors -H 'Content-Type: application/json' \
  -d '{"name":"Google","url":"https://www.google.com","checkType":"HTTP","intervalSeconds":60,"timeoutMs":5000}'
```

## Tests

```bash
cd backend && ./mvnw test                  # Testcontainers, so Docker must be running
cd frontend && npx ng test --watch=false   # Vitest
```
