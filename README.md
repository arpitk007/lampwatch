# Uptime Monitor

Checks websites on a schedule, records response times, and serves public status pages.

## Run locally

```bash
docker compose up -d --wait        # MySQL on 3307, Redis on 6381
cd backend && ./mvnw spring-boot:run   # API on http://localhost:8090
```

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
cd backend && ./mvnw test   # uses Testcontainers, so Docker must be running
```
