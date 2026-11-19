// Check-pipeline load test: runs a local target site, creates N monitors pointing at it (1-minute interval),
// then samples throughput and lag while the scheduler, Redis stream and workers do their job.
//
//   node loadtest/pipeline-load.mjs [--monitors 1000] [--delay 50] [--minutes 4] [--base http://localhost:8090]
//
// The app must run with uptime.allow-private-targets=true, because the target is on 127.0.0.1.
// Monitors are created under throwaway users (20 each, the per-account cap) and deleted at the end.
// Needs the local docker compose stack (mysql, redis containers) for the DB and stream samples.

import http from 'node:http';
import { execFileSync } from 'node:child_process';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc), []),
);
const BASE = args.base ?? 'http://localhost:8090';
const MONITORS = Number(args.monitors ?? 1000);
const DELAY_MS = Number(args.delay ?? 50);
const MINUTES = Number(args.minutes ?? 4);
const PER_USER = 20;
const TARGET_PORT = 9099;
const RUN = Date.now().toString(36);

// ---- target site: answers after DELAY_MS and counts hits per second
let hits = 0;
const target = http.createServer((req, res) => {
  hits++;
  setTimeout(() => res.end('ok'), DELAY_MS);
});
await new Promise((r) => target.listen(TARGET_PORT, '127.0.0.1', r));

// ---- minimal API client (same cookie/CSRF handling as api-load.mjs)
class Client {
  cookies = new Map();
  async req(method, path, body) {
    const headers = { Accept: 'application/json' };
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ');
    if (method !== 'GET' && this.cookies.has('XSRF-TOKEN')) headers['X-XSRF-TOKEN'] = this.cookies.get('XSRF-TOKEN');
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    for (const c of res.headers.getSetCookie()) {
      const [pair] = c.split(';');
      const eq = pair.indexOf('=');
      if (pair.slice(eq + 1) === '' || /max-age=0/i.test(c)) this.cookies.delete(pair.slice(0, eq));
      else this.cookies.set(pair.slice(0, eq), pair.slice(eq + 1));
    }
    const text = await res.text();
    if (res.status >= 400) throw new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 200)}`);
    return text ? JSON.parse(text) : null;
  }
}

const sql = (q) =>
  execFileSync('docker', ['exec', 'uptime-monitor-mysql-1', 'mysql', '-uuptime', '-puptime', 'uptime', '-N', '-e', q], {
    stdio: ['ignore', 'pipe', 'ignore'],
  }).toString().trim();
const redis = (...cmd) =>
  execFileSync('docker', ['exec', 'uptime-monitor-redis-1', 'redis-cli', ...cmd], { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();

async function setup() {
  const owners = [];
  const users = Math.ceil(MONITORS / PER_USER);
  for (let u = 0; u < users; u += 10) {
    await Promise.all(Array.from({ length: Math.min(10, users - u) }, async (_, k) => {
      const i = u + k;
      const c = new Client();
      await c.req('GET', '/api/auth/me').catch(() => {});
      await c.req('POST', '/api/auth/signup', { email: `pipe-${RUN}-${i}@example.test`, password: 'load-test-password' });
      await c.req('GET', '/api/auth/me');
      const ids = [];
      for (let m = 0; m < Math.min(PER_USER, MONITORS - i * PER_USER); m++) {
        const mon = await c.req('POST', '/api/monitors', {
          name: `pipe ${i}-${m}`, url: `http://127.0.0.1:${TARGET_PORT}/?u=${i}&m=${m}`,
          checkType: 'HTTP', keyword: null, intervalSeconds: 60, timeoutMs: 5000,
        });
        ids.push(mon.id);
      }
      owners.push({ c, ids });
    }));
  }
  return owners;
}

const owners = [];
async function cleanup() {
  for (const { c, ids } of owners) await Promise.all(ids.map((id) => c.req('DELETE', `/api/monitors/${id}`).catch(() => {})));
  target.close();
}
process.on('SIGINT', async () => { await cleanup(); process.exit(130); });

console.log(`monitors=${MONITORS} targetDelay=${DELAY_MS}ms minutes=${MINUTES}`);
const t0 = Date.now();
owners.push(...(await setup()));
console.log(`setup: ${owners.reduce((n, o) => n + o.ids.length, 0)} monitors in ${((Date.now() - t0) / 1000).toFixed(0)}s`);

const ids = owners.flatMap((o) => o.ids);
const idList = `(${ids.join(',')})`;
const needPerMin = ids.length; // every monitor once a minute

console.log('\n  t   hits/s  checks/min  need/min  overdue>30s  stream-lag  pending  p95-check-ms');
const samples = [];
const end = Date.now() + MINUTES * 60_000;
let lastHits = 0;
let lastT = Date.now();
while (Date.now() < end) {
  await new Promise((r) => setTimeout(r, 15_000));
  const now = Date.now();
  const hitRate = (hits - lastHits) / ((now - lastT) / 1000);
  lastHits = hits;
  lastT = now;
  const perMin = Number(sql(`select count(*) from check_results where monitor_id in ${idList} and checked_at > utc_timestamp(6) - interval 60 second`));
  const overdue = Number(sql(`select count(*) from monitors where id in ${idList} and paused = 0 and next_check_at < utc_timestamp(6) - interval 30 second`));
  const group = redis('XINFO', 'GROUPS', 'checks').split('\n');
  const field = (name) => Number(group[group.indexOf(name) + 1]);
  const p95 = sql(`select response_time_ms from check_results where monitor_id in ${idList} and checked_at > utc_timestamp(6) - interval 60 second and success = 1 order by response_time_ms desc limit 1 offset ${Math.floor(perMin * 0.05)}`) || '-';
  const row = { t: Math.round((now - t0) / 1000), hitRate, perMin, overdue, lag: field('lag'), pending: field('pending'), p95 };
  samples.push(row);
  console.log(
    String(row.t).padStart(4), hitRate.toFixed(1).padStart(8), String(perMin).padStart(11), String(needPerMin).padStart(9),
    String(overdue).padStart(12), String(row.lag).padStart(11), String(row.pending).padStart(8), String(p95).padStart(13),
  );
}

// Steady state = samples after the first minute (the first round is a 1000-monitor burst by design).
const steady = samples.filter((s) => s.t > 75);
const avg = (k) => steady.reduce((n, s) => n + s[k], 0) / Math.max(1, steady.length);
console.log(`\nsteady state: ${avg('perMin').toFixed(0)} checks/min of ${needPerMin} needed (${((avg('perMin') / needPerMin) * 100).toFixed(0)}%), avg overdue ${avg('overdue').toFixed(0)}, max stream lag ${Math.max(...steady.map((s) => s.lag))}`);
await cleanup();
console.log('cleanup: monitors deleted, target closed');
process.exit(0);
