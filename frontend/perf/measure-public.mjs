// Measures LCP / CLS / INP-proxy of the public pages on an emulated mid-range phone and fails
// when a median exceeds perf/budget.json. Run via `npm run perf:public` (builds first).
// PERF_BASE_URL=<url> measures a deployed instance instead of serving frontend/dist locally.
import { chromium } from '@playwright/test';
import { createServer } from 'node:http';
import { readFileSync, writeFileSync, existsSync, statSync, createReadStream } from 'node:fs';
import { extname, join, normalize, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const here = dirname(fileURLToPath(import.meta.url));
const cfg = JSON.parse(readFileSync(process.env.PERF_BUDGET ?? join(here, 'budget.json'), 'utf8'));
const dist = join(here, '..', 'dist', 'plantpal');
const root = existsSync(join(dist, 'browser')) ? join(dist, 'browser') : dist;
const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css',
  '.svg': 'image/svg+xml', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.webp': 'image/webp', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ico': 'image/x-icon',
  '.webmanifest': 'application/manifest+json',
};

// Static server for the prerendered build: /en/ -> en/index.html. Unknown paths 404 (no SPA
// fallback), so a page that is not prerendered is reported as absent, not measured as the shell.
function serve() {
  const server = createServer((req, res) => {
    const p = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^([/\\])+/, '');
    let file = join(root, p);
    if (!file.startsWith(root)) { res.writeHead(403).end(); return; }
    if (existsSync(file) && statSync(file).isDirectory()) file = join(file, 'index.html');
    else if (!existsSync(file) && existsSync(file + '.html')) file += '.html';
    if (!existsSync(file)) { res.writeHead(404).end('not found'); return; }
    res.writeHead(200, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream' });
    createReadStream(file).pipe(res);
  });
  return new Promise(ok => server.listen(0, '127.0.0.1', () => ok(server)));
}

const INIT = `
  window.__perf = { lcp: 0, lcpEl: '', cls: 0, shifts: [], inp: 0 };
  new PerformanceObserver(l => { for (const e of l.getEntries()) { window.__perf.lcp = e.startTime;
    window.__perf.lcpEl = e.element ? e.element.tagName.toLowerCase() + (typeof e.element.className === 'string' && e.element.className ? '.' + e.element.className.split(' ')[0] : '') : ''; } })
    .observe({ type: 'largest-contentful-paint', buffered: true });
  new PerformanceObserver(l => { for (const e of l.getEntries()) if (!e.hadRecentInput) { window.__perf.cls += e.value;
    window.__perf.shifts.push((e.sources && e.sources[0] && e.sources[0].node ? e.sources[0].node.nodeName : '?') + ':' + e.value.toFixed(3)); } })
    .observe({ type: 'layout-shift', buffered: true });
  new PerformanceObserver(l => { for (const e of l.getEntries()) window.__perf.inp = Math.max(window.__perf.inp, e.duration); })
    .observe({ type: 'event', durationThreshold: 16, buffered: true });
`;

const median = a => {
  const s = [...a].sort((x, y) => x - y);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

async function runOnce(browser, url, interaction) {
  const d = cfg.device;
  const ctx = await browser.newContext({
    viewport: d.viewport, deviceScaleFactor: d.deviceScaleFactor,
    isMobile: d.isMobile, hasTouch: d.hasTouch, userAgent: d.userAgent,
  });
  try {
    const page = await ctx.newPage();
    await page.addInitScript(INIT);
    const cdp = await ctx.newCDPSession(page);
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: d.cpuSlowdown });
    await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions', {
      offline: false, latency: d.network.latencyMs,
      downloadThroughput: (d.network.downloadKbps * 1024) / 8,
      uploadThroughput: (d.network.uploadKbps * 1024) / 8,
    });
    const resp = await page.goto(url, { waitUntil: 'load', timeout: 60000 });
    const status = resp ? resp.status() : 0;
    if (status !== 200) return { status };
    await page.waitForTimeout(2500); // let LCP settle
    let target = null;
    for (const sel of interaction) {
      const h = await page.$(sel);
      if (h && (await h.isVisible())) { target = h; break; }
    }
    if (target) {
      // Keep the page alive while the event timing is recorded: links must not navigate away.
      await page.evaluate(() => addEventListener('click', e => {
        if (e.target.closest && e.target.closest('a')) e.preventDefault();
      }, true));
      await target.tap();
      await page.waitForTimeout(800);
    }
    const r = await page.evaluate(() => window.__perf);
    return { status, interacted: !!target, ...r };
  } finally { await ctx.close(); }
}

const base = process.env.PERF_BASE_URL ? process.env.PERF_BASE_URL.replace(/\/$/, '') : undefined;
let server;
let origin = base;
if (!base) {
  if (!existsSync(root)) { console.error(`No build at ${root}; run npm run build:prod first.`); process.exit(2); }
  server = await serve();
  origin = `http://127.0.0.1:${server.address().port}`;
}
const browser = await chromium.launch();
const rows = [];
const notes = [];
let failed = false;
try {
  for (const lang of cfg.languages) {
    for (const pg of cfg.pages) {
      const url = origin + pg.path.replace('{lang}', lang);
      const runs = [];
      for (let i = 0; i < cfg.runs; i++) {
        const r = await runOnce(browser, url, pg.interaction);
        if (r.status !== 200) {
          runs.length = 0;
          notes.push(`${pg.id}/${lang}: HTTP ${r.status} at ${url}${pg.optional ? ' (optional page not shipped yet, skipped)' : ' (FAILED)'}`);
          if (!pg.optional) failed = true;
          break;
        }
        runs.push(r);
      }
      if (!runs.length) { rows.push({ page: pg.id, lang, skipped: true }); continue; }
      const row = {
        page: pg.id, lang, runs: runs.length,
        lcpMs: Math.round(median(runs.map(r => r.lcp))),
        cls: +median(runs.map(r => r.cls)).toFixed(4),
        inpMs: Math.round(median(runs.map(r => r.inp))),
        interacted: runs.every(r => r.interacted),
        lcpElement: runs[0].lcpEl, shifts: runs[0].shifts.slice(0, 3),
      };
      row.pass = row.lcpMs <= cfg.budget.lcpMs && row.cls <= cfg.budget.cls && row.inpMs <= cfg.budget.inpMs;
      if (!row.interacted) notes.push(`${pg.id}/${lang}: no interaction target found; INP is not meaningful`);
      if (!row.pass) failed = true;
      rows.push(row);
    }
  }
} finally {
  await browser.close();
  if (server) server.close();
}

let commit = 'unknown';
try { commit = execSync('git rev-parse --short HEAD', { cwd: here }).toString().trim(); } catch { /* not a git checkout */ }
const out = {
  date: new Date().toISOString(), commit, target: base ?? 'local dist',
  budget: cfg.budget, device: cfg.device.name, rows, notes,
};
writeFileSync(process.env.PERF_OUT ?? join(here, 'last-run.json'), JSON.stringify(out, null, 2));

console.log(`\nBudget: LCP<=${cfg.budget.lcpMs}ms CLS<=${cfg.budget.cls} INP<=${cfg.budget.inpMs}ms | ${cfg.device.name}, ${cfg.device.cpuSlowdown}x CPU, ${cfg.device.network.name} | median of ${cfg.runs}\n`);
console.log('| page | lang | LCP ms | CLS | INP ms | result | LCP element |\n|---|---|---|---|---|---|---|');
for (const r of rows) {
  console.log(r.skipped
    ? `| ${r.page} | ${r.lang} | - | - | - | skipped | - |`
    : `| ${r.page} | ${r.lang} | ${r.lcpMs} | ${r.cls} | ${r.inpMs} | ${r.pass ? 'PASS' : 'FAIL'} | ${r.lcpElement} |`);
}
for (const n of notes) console.log(`note: ${n}`);
process.exit(failed ? 1 : 0);
