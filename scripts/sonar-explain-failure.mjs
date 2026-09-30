import { readFileSync } from 'node:fs';
// Explains a failed SonarQube quality gate in the CI log, using only the read API.
// Usage: node scripts/sonar-explain-failure.mjs <projectKey>:<dir> ...   (env: SONAR_HOST_URL, SONAR_TOKEN,
// CANDIDATE_FILES = file listing repo-relative source paths, one per line (git ls-files))
// The analysis token cannot use the tree endpoints, so uncovered files come from per-file measures.
// Never prints the token; always exits 0 so it cannot change the gate's verdict.
const host = (process.env.SONAR_HOST_URL || '').replace(/\/$/, '');
const token = process.env.SONAR_TOKEN || '';
const auth = 'Basic ' + Buffer.from(`${token}:`).toString('base64');

async function api(path, params) {
  const url = `${host}/api/${path}?${new URLSearchParams(params)}`;
  const res = await fetch(url, { headers: { Authorization: auth } });
  if (!res.ok) throw new Error(`GET /api/${path} -> HTTP ${res.status}`);
  return res.json();
}

const candidates = process.env.CANDIDATE_FILES
  ? readFileSync(process.env.CANDIDATE_FILES, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean)
  : [];
const RELATION = { GT: '>', LT: '<' };
const periodValue = (m) => m.period?.value ?? m.periods?.[0]?.value ?? m.value;

function formatCondition(c) {
  const rel = RELATION[c.comparator] ?? c.comparator;
  return `${c.metricKey} ${c.actualValue} ${rel} ${c.errorThreshold}`;
}

async function explain(key, dir) {
  const status = await api('qualitygates/project_status', { projectKey: key });
  const ps = status.projectStatus;
  if (ps.status === 'OK') {
    console.log(`[${key}] quality gate passed`);
    return;
  }
  console.log(`\n=== [${key}] quality gate ${ps.status} ===`);
  console.log('Failed conditions:');
  for (const c of ps.conditions.filter((x) => x.status === 'ERROR')) {
    console.log(`  ${formatCondition(c)}`);
  }

  const issues = await api('issues/search', {
    componentKeys: key, resolved: 'false', inNewCodePeriod: 'true', ps: '500',
  });
  console.log(`New-code issues (${issues.total}):`);
  for (const i of issues.issues) {
    const file = i.component.split(':').slice(1).join(':');
    console.log(`  ${i.severity} ${i.rule} ${file}:${i.line ?? '?'} ${i.message}`);
  }

  console.log('Files with uncovered new lines:');
  const prefix = `${dir}/`;
  const files = candidates.filter((f) => f.startsWith(prefix)).map((f) => f.slice(prefix.length));
  for (const path of files) {
    let res;
    try {
      res = await api('measures/component', {
        component: `${key}:${path}`, metricKeys: 'new_uncovered_lines,new_lines_to_cover',
      });
    } catch {
      continue;
    }
    const v = Object.fromEntries(res.component.measures.map((m) => [m.metric, Number(periodValue(m))]));
    if (!v.new_uncovered_lines) continue;
    console.log(`  ${path} uncovered ${v.new_uncovered_lines} of ${v.new_lines_to_cover} new lines to cover`);
  }
}

for (const arg of process.argv.slice(2)) {
  const [key, dir] = arg.split(':');
  try {
    await explain(key, dir);
  } catch (e) {
    console.log(`[${key}] could not fetch failure details: ${String(e.message).replaceAll(token || '__none__', '***')}`);
  }
}
