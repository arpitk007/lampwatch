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

Example:

```bash
curl -X POST localhost:8090/api/monitors -H 'Content-Type: application/json' \
  -d '{"name":"Google","url":"https://www.google.com","checkType":"HTTP","intervalSeconds":60,"timeoutMs":5000}'
```

## Tests

```bash
cd backend && ./mvnw test   # uses Testcontainers, so Docker must be running
```
