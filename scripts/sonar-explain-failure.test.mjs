// Unit tests for scripts/sonar-explain-failure.mjs on recorded-shape SonarQube answers
// (scripts/sonar-fixtures/*.json), plus an end-to-end dry run against a local stub server.
// Run: node --test scripts/
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync, readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { explainProject, makeFetchJson, renderMarkdown, renderText } from './sonar-explain-failure.mjs';

const fixture = (name) => JSON.parse(readFileSync(new URL(`./sonar-fixtures/${name}.json`, import.meta.url), 'utf8'));
const HOST = 'http://sonar.test:9000';

function stub(routes, calls = []) {
  return async (path, params) => {
    calls.push({ path, params });
    const answer = routes[path];
    if (answer instanceof Error) throw answer;
    if (!answer) throw new Error(`GET /api/${path} -> HTTP 404`);
    return answer;
  };
}

const failing = (status, extra = {}) => ({
  'qualitygates/project_status': fixture(status),
  'issues/search': fixture('issues'),
  ...extra,
});

test('failing coverage condition: condition table, issues, lowest-coverage files, marker', async () => {
  const calls = [];
  const fetchJson = stub(failing('status-coverage-error', { 'measures/component_tree': fixture('component-tree') }), calls);
  const report = await explainProject({ fetchJson, host: HOST, key: 'plantpal-backend' });
  const text = renderText([report]);

  assert.match(text, /\[plantpal-backend\] quality gate ERROR/);
  assert.match(text, /Coverage\s+new_coverage\s+< 80%\s+71\.4%\s+yes/);
  assert.match(text, /Issues\s+new_violations\s+> 0\s+2\s+yes/);
  assert.doesNotMatch(text, /new_duplicated_lines_density/, 'passing conditions are not listed');
  assert.match(text, /java:S2068\s+BLOCKER\s+VULNERABILITY\s+src\/main\/java\/com\/plantpal\/user\/AuthService\.java:42\s+Review this/);
  assert.match(text, /java:S1135\s+INFO\s+CODE_SMELL\s+src\/main\/java\/com\/plantpal\/shared\/Probe\.java:\?/);
  const files = text.split('Lowest new-code coverage')[1];
  assert.doesNotMatch(files, /Covered\.java/, 'fully covered files are left out');
  assert.match(files, /Probe\.java\s+40%\s+6\s+10/);
  assert.equal(text.trimEnd().split('\n').pop(), 'SONAR-GATE-FAILED: plantpal-backend=new_coverage,new_violations');

  const search = calls.find((c) => c.path === 'issues/search').params;
  assert.equal(search.inNewCodePeriod, 'true');
  assert.equal(search.ps, '20');
  assert.equal(search.s, 'SEVERITY');
  assert.equal(search.asc, 'false');
});

test('pull request analyses pass the pullRequest parameter to every call', async () => {
  const calls = [];
  const fetchJson = stub(failing('status-coverage-error', { 'measures/component_tree': fixture('component-tree') }), calls);
  const report = await explainProject({ fetchJson, host: HOST, key: 'plantpal-backend', pullRequest: '230' });
  assert.equal(calls.length, 3);
  for (const c of calls) assert.equal(c.params.pullRequest, '230', c.path);
  assert.equal(report.url, `${HOST}/dashboard?id=plantpal-backend&pullRequest=230`);
});

test('failing duplication condition: no coverage file list', async () => {
  const calls = [];
  const report = await explainProject({ fetchJson: stub(failing('status-duplication-error'), calls), host: HOST, key: 'plantpal-frontend' });
  const text = renderText([report]);
  assert.match(text, /Duplicated lines\s+new_duplicated_lines_density\s+> 3%\s+7\.9%\s+yes/);
  assert.doesNotMatch(text, /Lowest new-code coverage/);
  assert.ok(!calls.some((c) => c.path.startsWith('measures/')));
  assert.match(text, /SONAR-GATE-FAILED: plantpal-frontend=new_duplicated_lines_density$/);
});

test('failing security rating is shown as letters', async () => {
  const report = await explainProject({ fetchJson: stub(failing('status-security-rating-error')), host: HOST, key: 'plantpal-frontend' });
  const text = renderText([report]);
  assert.match(text, /Security rating\s+new_security_rating\s+> A\s+D\s+yes/);
  assert.match(text, /SONAR-GATE-FAILED: plantpal-frontend=new_security_rating$/);
});

test('passing gate produces nothing and asks for nothing else', async () => {
  const calls = [];
  const report = await explainProject({ fetchJson: stub({ 'qualitygates/project_status': fixture('status-ok') }, calls), host: HOST, key: 'plantpal-backend' });
  assert.equal(report, null);
  assert.equal(calls.length, 1);
  assert.equal(renderText([]), '');
  assert.equal(renderMarkdown([]), '');
});

test('API error: says why, links the dashboard, still marks the failure', async () => {
  const fetchJson = stub({ 'qualitygates/project_status': new Error('GET /api/qualitygates/project_status -> HTTP 401') });
  const report = await explainProject({ fetchJson, host: HOST, key: 'plantpal-backend' });
  const text = renderText([report]);
  assert.match(text, /details unavailable/);
  assert.match(text, /HTTP 401/);
  assert.match(text, new RegExp(`Open the analysis: ${HOST}/dashboard\\?id=plantpal-backend`));
  assert.match(text, /SONAR-GATE-FAILED: plantpal-backend=details-unavailable$/);
});

test('refused component_tree falls back to per-file measures; a second failure is reported, not thrown', async () => {
  const perFile = {
    component: { key: 'k', measures: [
      { metric: 'new_coverage', period: { value: '50.0' } },
      { metric: 'new_uncovered_lines', period: { value: '4' } },
      { metric: 'new_lines_to_cover', period: { value: '8' } },
    ] },
  };
  const routes = failing('status-coverage-error', { 'measures/component_tree': new Error('HTTP 403'), 'measures/component': perFile });
  const report = await explainProject({ fetchJson: stub(routes), host: HOST, key: 'plantpal-backend', candidates: ['src/A.java'] });
  assert.deepEqual(report.files.map((f) => f.path), ['src/A.java']);

  const none = await explainProject({ fetchJson: stub({ ...routes, 'measures/component': undefined }), host: HOST, key: 'plantpal-backend' });
  assert.deepEqual(none.files, []);
  assert.match(none.notes.join(), /uncovered files unavailable: HTTP 403/);
  assert.match(renderText([none]), /note: uncovered files unavailable/);
});

test('markdown report has the same tables and the marker', async () => {
  const fetchJson = stub(failing('status-coverage-error', { 'measures/component_tree': fixture('component-tree') }));
  const md = renderMarkdown([await explainProject({ fetchJson, host: HOST, key: 'plantpal-backend' })]);
  assert.match(md, /^## SonarQube quality gate failed/);
  assert.match(md, /\| Coverage \| new_coverage \| < 80% \| 71\.4% \| yes \|/);
  assert.match(md, /\| java:S2068 \| BLOCKER \| VULNERABILITY \| src\/main\/java\/com\/plantpal\/user\/AuthService\.java:42 \|/);
  assert.match(md, /`SONAR-GATE-FAILED: plantpal-backend=new_coverage,new_violations`/);
});

test('a network failure message never carries the token', async () => {
  const token = 'squ_secret_value';
  const fetchImpl = async () => { throw new Error(`connect refused for ${token}`); };
  await assert.rejects(makeFetchJson({ host: HOST, token, fetchImpl })('x', {}), (e) => !e.message.includes(token) && e.message.includes('***'));
});

test('dry run: the CLI prints the table, writes the job summary, exits 0 and never prints the token', async () => {
  const token = 'squ_dry_run_token_123';
  const seen = [];
  const server = createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    seen.push({ path: url.pathname, auth: req.headers.authorization });
    const body = {
      '/api/qualitygates/project_status': fixture('status-coverage-error'),
      '/api/issues/search': fixture('issues'),
      '/api/measures/component_tree': fixture('component-tree'),
    }[url.pathname];
    res.writeHead(body ? 200 : 404, { 'content-type': 'application/json' });
    res.end(JSON.stringify(body ?? {}));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const summary = join(mkdtempSync(join(tmpdir(), 'sonar-explain-')), 'summary.md');
  const script = fileURLToPath(new URL('./sonar-explain-failure.mjs', import.meta.url));
  const child = spawn(process.execPath, [script, 'plantpal-backend'], {
    env: { PATH: process.env.PATH, SONAR_HOST_URL: `http://127.0.0.1:${server.address().port}`, SONAR_TOKEN: token, GITHUB_STEP_SUMMARY: summary },
  });
  let out = '';
  child.stdout.on('data', (d) => { out += d; });
  child.stderr.on('data', (d) => { out += d; });
  const code = await new Promise((resolve) => child.on('close', resolve));
  server.close();

  assert.equal(code, 0);
  assert.match(out, /Coverage\s+new_coverage\s+< 80%\s+71\.4%\s+yes/);
  assert.match(out, /SONAR-GATE-FAILED: plantpal-backend=new_coverage,new_violations/);
  assert.ok(!out.includes(token));
  assert.match(readFileSync(summary, 'utf8'), /## SonarQube quality gate failed/);
  assert.ok(seen.every((s) => s.auth === `Basic ${Buffer.from(`${token}:`).toString('base64')}`));
});
