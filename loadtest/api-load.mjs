// API load test: signs up N users, gives each a few paused monitors, then runs
// concurrent virtual users against the dashboard and public status page endpoints.
//
//   node loadtest/api-load.mjs [--base http://localhost:8090] [--users 50] [--vus 50] [--seconds 30]
//
// No dependencies (Node 18+ fetch). Run with the proxy env unset for localhost.
// The rate limiter allows 5 sign-ups per IP per hour and 120 anonymous requests per IP per minute, so start
// the app with raised limits for this test, e.g.
//   --uptime.rate-limit.signups-per-ip-per-hour=10000 --uptime.rate-limit.per-ip-per-minute=1000000
// (the per-user budget of 300/min stays on). Monitors it creates are deleted at the end.

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc), []),
);
const BASE = args.base ?? 'http://localhost:8090';
const USERS = Number(args.users ?? 50);
const VUS = Number(args.vus ?? 50);
const SECONDS = Number(args.seconds ?? 30);
const MONITORS_PER_USER = Number(args.monitors ?? 5);
const RUN = Date.now().toString(36);

/** Minimal cookie jar, enough for SESSION and XSRF-TOKEN. */
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
      const name = pair.slice(0, eq);
      const value = pair.slice(eq + 1);
      if (value === '' || /max-age=0/i.test(c)) this.cookies.delete(name);
      else this.cookies.set(name, value);
    }
    const text = await res.text();
    return { status: res.status, body: text };
  }

  async must(method, path, body) {
    const r = await this.req(method, path, body);
    if (r.status >= 400) throw new Error(`${method} ${path} -> ${r.status} ${r.body.slice(0, 200)}`);
    return r.body ? JSON.parse(r.body) : null;
  }
}

async function setupUser(i) {
  const c = new Client();
  await c.req('GET', '/api/auth/me'); // sets XSRF-TOKEN
  await c.must('POST', '/api/auth/signup', { email: `load-${RUN}-${i}@example.test`, password: 'load-test-password' });
  await c.req('GET', '/api/auth/me'); // token may rotate after sign-in
  const ids = [];
  for (let m = 0; m < MONITORS_PER_USER; m++) {
    const mon = await c.must('POST', '/api/monitors', {
      name: `load ${i}-${m}`, url: `https://example.com/?u=${i}&m=${m}`,
      checkType: 'HTTP', keyword: null, intervalSeconds: 3600, timeoutMs: 5000,
    });
    await c.must('POST', `/api/monitors/${mon.id}/pause`); // API test only; don't hit example.com
    ids.push(mon.id);
  }
  return { c, ids };
}

const stats = new Map(); // endpoint -> { lat: [], codes: Map }
function record(name, ms, status) {
  if (!stats.has(name)) stats.set(name, { lat: [], codes: new Map() });
  const s = stats.get(name);
  s.lat.push(ms);
  s.codes.set(status, (s.codes.get(status) ?? 0) + 1);
}

async function timed(name, fn) {
  const t = performance.now();
  let status;
  try {
    status = (await fn()).status;
  } catch (e) {
    status = 'ERR';
  }
  record(name, performance.now() - t, status);
}

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];

let created = [];

async function main() {
  console.log(`base=${BASE} users=${USERS} vus=${VUS} seconds=${SECONDS}`);
  const t0 = performance.now();
  const users = [];
  for (let i = 0; i < USERS; i += 10) {
    users.push(...(await Promise.all(Array.from({ length: Math.min(10, USERS - i) }, (_, k) => setupUser(i + k)))));
  }
  const slug = `load-${RUN}`;
  await users[0].c.must('PUT', '/api/status-page', { slug, title: 'Load test', monitorIds: users[0].ids });
  created = users;
  console.log(`setup: ${users.length} users, ${users.length * MONITORS_PER_USER} monitors in ${((performance.now() - t0) / 1000).toFixed(1)}s`);

  const anon = new Client();
  const deadline = performance.now() + SECONDS * 1000;
  // Each VU mimics an open dashboard tab, hammered without the 15 s pause:
  // list + heartbeats, plus an anonymous status page visit.
  await Promise.all(Array.from({ length: VUS }, async (_, v) => {
    const { c } = users[v % users.length];
    while (performance.now() < deadline) {
      await timed('GET /api/monitors', () => c.req('GET', '/api/monitors'));
      await timed('GET /api/monitors/heartbeats', () => c.req('GET', '/api/monitors/heartbeats'));
      await timed('GET /api/public/status-pages/{slug}', () => anon.req('GET', `/api/public/status-pages/${slug}`));
    }
  }));

  console.log('\nendpoint                               reqs    rps   p50ms  p95ms  p99ms  maxms  codes');
  for (const [name, s] of stats) {
    const sorted = s.lat.sort((a, b) => a - b);
    const codes = [...s.codes].map(([k, n]) => `${k}:${n}`).join(' ');
    console.log(
      name.padEnd(38),
      String(sorted.length).padStart(5),
      (sorted.length / SECONDS).toFixed(0).padStart(6),
      pct(sorted, 50).toFixed(0).padStart(7),
      pct(sorted, 95).toFixed(0).padStart(6),
      pct(sorted, 99).toFixed(0).padStart(6),
      sorted[sorted.length - 1].toFixed(0).padStart(6),
      ' ' + codes,
    );
  }
}

async function cleanup() {
  for (const { c, ids } of created) await Promise.all(ids.map((id) => c.req('DELETE', `/api/monitors/${id}`).catch(() => {})));
}

main().then(cleanup).catch(async (e) => {
  await cleanup();
  console.error(e.message);
  process.exit(1);
});
