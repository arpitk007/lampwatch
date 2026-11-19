# Lampwatch

An uptime monitor: checks websites on a schedule, records response times, alerts by email or Telegram when a site goes down, and serves public status pages.

## Run locally

```bash
docker compose up -d --wait               # MySQL 3307, Redis 6381, Mailpit 1026 (UI on 8026)
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
- Every `/api/**` request is rate limited in Redis with fixed one-minute windows: 300 per signed-in user, 120 per IP for anonymous calls (status pages, sign-in, sign-up), and 5 new accounts per IP per hour. Over budget returns `429` with `Retry-After`. The counters are shared by every instance, and if Redis is unreachable the limiter lets requests through (`uptime.rate-limit.*`).
- Behind Caddy, `server.forward-headers-strategy=native` makes Tomcat take the client address from `X-Forwarded-For`, but only when the request comes from a private-network proxy. Without it every visitor would share Caddy's address, and 30 failed sign-ins from anyone would lock sign-in for everyone.

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
| GET | `/api/monitors/{id}/response-times?hours=24` | Hourly average response time (up to 720 hours) |
| GET | `/api/monitors/{id}/incidents?limit=20` | Incidents, newest first |
| GET | `/api/channels` | Alert channels with their last delivery |
| POST | `/api/channels` | Add an email or Telegram channel |
| DELETE | `/api/channels/{id}` | Remove a channel |
| POST | `/api/channels/{id}/test` | Send a test alert (once a minute) |
| GET | `/api/status-page` | Your status page settings |
| PUT | `/api/status-page` | Create or update your status page |
| DELETE | `/api/status-page` | Take your status page offline |
| GET | `/api/public/status-pages/{address}` | The public page (no sign-in, cached 30 s) |
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
4. The worker runs the `Checker` for the monitor's type, stores the result and updates the status in one transaction, then acknowledges. A monitor turns `DOWN` after 3 failures in a row and back `UP` after 2 passes (`GET /api/settings/status-rules` reports the thresholds).
5. Delivery is at-least-once. Each result stores the stream message id in a unique `job_id` column and is written with `INSERT IGNORE`, so a redelivered message is recorded once.
6. If a worker dies mid-check, its message stays pending. The reclaimer claims messages idle for over 60 seconds and runs them again. After 3 deliveries a message goes to the `checks:dead` stream with the reason, so one bad message can't loop forever.
7. Messages older than 5 minutes are dropped rather than checked late; the next scheduler tick sends a fresh one.

`uptime.scheduler-enabled` and `uptime.worker-enabled` let an instance run only the scheduler or only workers, so the two can be scaled separately.

## Status and incidents

Each monitor's status is a small state machine (state pattern, `incidents/MonitorState.java`):

| From | Check result | To | Effect |
|---|---|---|---|
| Unknown, Up, Slow | Pass under 3 s | Up | |
| Unknown, Up, Slow | Pass over 3 s | Slow (`DEGRADED`) | |
| Unknown, Up, Slow | 1st or 2nd failure in a row | unchanged | treated as a blip |
| Unknown, Up, Slow | 3rd failure in a row | Down | incident opens |
| Down | 1st pass | Down | |
| Down | 2nd pass in a row | Up or Slow | incident resolves |

- Requiring several results in a row in both directions stops a flaky site from flapping between up and down.
- Results for one monitor are applied under a row lock (`SELECT … FOR UPDATE`), so two workers can't apply them out of order.
- Status fields are excluded from optimistic locking, so a check landing while you edit a monitor doesn't fail your save, and your save doesn't undo the check.
- MySQL allows only one open incident per monitor: a generated column is `1` while open and `NULL` once resolved, under a unique key with `monitor_id`.
- Opening or resolving an incident publishes an event after the transaction commits; alerts subscribe to these.

## Alerts

When an incident opens or resolves, every alert channel of the monitor's owner gets a message: email, or Telegram through a bot.

- **Transactional outbox.** Alert rows go into `notification_outbox` inside the transaction that opens or resolves the incident (`@TransactionalEventListener(BEFORE_COMMIT)`). Both commit together, so a crash can't lose an alert and a rolled-back check can't send one.
- **Exactly one alert per channel per event.** A unique key on `(channel_id, incident_id, kind)` with `INSERT IGNORE` makes a repeated event harmless.
- **Dispatcher.** Every 5 seconds it claims due rows with `SELECT … FOR UPDATE OF o SKIP LOCKED`, leases them for 2 minutes, then sends. Several instances never send the same alert, and one that dies mid-send leaves the row to be retried.
- **Retries.** Failures back off 30 s, 2 min, 8 min, 32 min, then hourly. After 6 attempts the row is `FAILED`, and the Alerts page shows the error.
- **Senders** implement `AlertSender` (strategy pattern), so adding Slack or Discord is one class.
- **Abuse limits.** A channel can point at any address, so each account has at most 5 channels and one test alert per channel per minute.

Locally, email goes to Mailpit; open http://localhost:8026 to read it. In production set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `ALERTS_FROM`, `APP_URL`, and `TELEGRAM_BOT_TOKEN` for Telegram.

## Status pages

Each account can publish one public page at `/s/<address>` with chosen monitors: current status, 30 days of daily uptime bars, uptime over 30 days, and incidents from the past 14 days.

- **Hourly rollups.** Every recorded check also updates `check_rollups_hourly` in the same transaction (`INSERT … ON DUPLICATE KEY UPDATE`). A 30-day page reads about 720 rollup rows per monitor instead of tens of thousands of raw checks. Redelivered checks are skipped before the rollup, so they count once.
- **Retention.** A nightly job deletes raw results after 7 days, rollups after 90 days and sent alerts after 30 days, in batches of 5,000 so no statement holds locks for long. An index on `checked_at` keeps those deletes from scanning the table.
- **Redis cache-aside.** The public page is built at most once per 30 seconds per address and served from Redis in between, which absorbs the traffic spike when a status link is shared during an outage. Saving the page evicts it. The response also sends `Cache-Control: public, max-age=30`.
- **Privacy.** Public pages show service names and status only, never URLs or error messages, which can reveal internal hosts.
- **Honest numbers.** Any recorded failure caps displayed uptime at 99.99%, so real downtime never rounds up to 100%.
- **Accessible bars.** Each day's band is shown by color and by stripes for major downtime, with a legend, hover and keyboard readouts, and a day-by-day table. The bar colors passed the dataviz palette validator for colorblind separation.

## SSRF protection

Anyone can sign up and enter a URL, and the checker fetches it from inside the server's network. Without a guard, a monitor could reach localhost, MySQL or Redis, other internal services, or the cloud metadata endpoint (`169.254.169.254`) that hands out credentials.

- Only `http` and `https` URLs, and no `user:password@` in the URL.
- The hostname is resolved, and every address is rejected if it is loopback, private (10/8, 172.16/12, 192.168/16), link-local, carrier-grade NAT (100.64/10), unique-local IPv6 (fc00::/7), multicast or reserved, including IPv4 hidden inside IPv6 (`::ffff:10.0.0.1`).
- This runs when a monitor is saved and again before every check, because a domain can be re-pointed later.
- Redirects are followed by hand, at most 5, and each hop is checked, so a public URL can't bounce the checker into the private network.
- The remaining gap, DNS that changes between the check and the connection (DNS rebinding), is closed at the network layer: the production server's firewall blocks outgoing traffic from the app to private ranges.

## Response time chart

The monitor page charts the hourly average response time of successful checks for the last 24 hours or 7 days, read from the hourly rollups. Hours without checks break the line instead of being bridged, hours with failures get a red tick plus text in the tooltip, and the crosshair works with the mouse or the arrow keys. An hourly table gives the same numbers without the chart.

## Load tests

Two scripts in `loadtest/`, no dependencies beyond Node 18+. Numbers below are from one 4-core desktop running the app, MySQL, Redis and the load generator together, so they are a floor, and runs vary by about ±40%.

**API** (`api-load.mjs`): 50 signed-in users, 50 concurrent clients with no think time, each loading the monitor list, the heartbeats and a public status page.

| | Requests/s (all three) | p95 | Errors |
|---|---|---|---|
| Typical warm run | 300–350 | 200–270 ms | 0 |

The app is CPU-bound (about 2.2 of 4 cores busy while MySQL ran 2–3 queries at a time), so the connection pool is not the limit. An open dashboard tab makes about 8 requests a minute, so one core serves roughly 1,000 open tabs. The rate limiter's cost (one Redis `INCR` per request) was below the run-to-run noise.

**Check pipeline** (`pipeline-load.mjs`): 1,000 monitors on a 1-minute interval against a local site that answers after a fixed delay.

| Workers per instance | Site delay | Checks/min done | Needed | Queue |
|---|---|---|---|---|
| 8 | 50 ms | 1,000 | 1,000 | drained every minute |
| 8 | 500 ms | 768 | 1,000 | grew without limit (1,372 after 4 min) |
| 32 | 500 ms | 1,000 | 1,000 | drained every minute |

Workers block on one check at a time, so throughput is about workers ÷ (site time + ~70 ms of recording). With 8 workers, slow sites made checks fall behind silently: the scheduler kept enqueueing on time, the queue grew, and checks older than 5 minutes were dropped. The default is now 32 workers (`uptime.worker-threads`) and a 20-connection pool (`DB_POOL_SIZE`); a worker holds a connection only while it records a result. That is about 3,000 one-minute monitors per instance for sites answering in half a second, and more instances add workers through the same consumer group.

## Tests

```bash
cd backend && ./mvnw test                  # Testcontainers, so Docker must be running
cd frontend && npx ng test --watch=false   # Vitest
```
