// Explains a failed SonarQube quality gate in the CI log and the GitHub job summary,
// using only the read API and the credential the scanner already uses.
//
// Usage: node scripts/sonar-explain-failure.mjs <projectKey>[:<repo dir>] ...
// Env:   SONAR_HOST_URL, SONAR_TOKEN (never printed),
//        SONAR_PULL_REQUEST  optional; set only when the scanner is given sonar.pullrequest.key
//                            (Community Edition has no PR analyses: leave unset there),
//        CANDIDATE_FILES     optional file listing repo-relative paths (git ls-files), used for
//                            the per-file coverage fallback when component_tree is refused,
//        GITHUB_STEP_SUMMARY set by Actions; the markdown report is appended to it.
// A passing gate prints nothing. An API error is reported, never thrown: exit code is 0.
import { appendFileSync, readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const METRICS = {
  new_coverage: ['Coverage', '%'],
  new_line_coverage: ['Line coverage', '%'],
  new_branch_coverage: ['Condition coverage', '%'],
  new_duplicated_lines_density: ['Duplicated lines', '%'],
  new_violations: ['Issues', ''],
  new_security_rating: ['Security rating', 'rating'],
  new_reliability_rating: ['Reliability rating', 'rating'],
  new_maintainability_rating: ['Maintainability rating', 'rating'],
  new_security_hotspots_reviewed: ['Security hotspots reviewed', '%'],
  new_security_review_rating: ['Security review rating', 'rating'],
  security_rating: ['Security rating (overall)', 'rating'],
  reliability_rating: ['Reliability rating (overall)', 'rating'],
  sqale_rating: ['Maintainability rating (overall)', 'rating'],
  coverage: ['Coverage (overall)', '%'],
  duplicated_lines_density: ['Duplicated lines (overall)', '%'],
};
const COMPARATOR = { GT: '>', LT: '<', EQ: '=', NE: '!=' };
const RATING = ['', 'A', 'B', 'C', 'D', 'E'];
const MAX_ISSUES = 20;
const MAX_FILES = 10;
const COVERAGE_METRICS = 'new_coverage,new_uncovered_lines,new_lines_to_cover';

export const isNewCodeMetric = (key) => key.startsWith('new_');

export function metricName(key) {
  return METRICS[key]?.[0] ?? key.replace(/^new_/, '').replaceAll('_', ' ');
}

export function formatValue(key, value) {
  const kind = METRICS[key]?.[1] ?? '';
  if (kind === 'rating') return RATING[Math.round(Number(value))] || String(value);
  return kind === '%' ? `${value}%` : String(value);
}

export function describeCondition(c) {
  return {
    key: c.metricKey,
    name: metricName(c.metricKey),
    comparator: COMPARATOR[c.comparator] ?? c.comparator,
    threshold: formatValue(c.metricKey, c.errorThreshold),
    actual: formatValue(c.metricKey, c.actualValue),
    newCode: isNewCodeMetric(c.metricKey),
  };
}

const periodValue = (m) => m.period?.value ?? m.periods?.[0]?.value ?? m.value;
const measuresOf = (component) =>
  Object.fromEntries((component.measures ?? []).map((m) => [m.metric, Number(periodValue(m))]));
const filePath = (component, key) => (component.startsWith(`${key}:`) ? component.slice(key.length + 1) : component);
const byCoverage = (a, b) => (a.new_coverage ?? 0) - (b.new_coverage ?? 0);

export function dashboardUrl(host, key, pullRequest) {
  const pr = pullRequest ? `&pullRequest=${encodeURIComponent(pullRequest)}` : '';
  return `${host}/dashboard?id=${encodeURIComponent(key)}${pr}`;
}

async function lowestCoverageFiles(fetchJson, key, pr, candidates) {
  try {
    const tree = await fetchJson('measures/component_tree', {
      ...pr, component: key, strategy: 'leaves', qualifiers: 'FIL', ps: '500', metricKeys: COVERAGE_METRICS,
    });
    return tree.components
      .map((c) => ({ path: filePath(c.key, key), ...measuresOf(c) }))
      .filter((f) => f.new_uncovered_lines > 0)
      .sort(byCoverage)
      .slice(0, MAX_FILES);
  } catch (treeError) {
    // The analysis token is refused by the tree endpoints on some servers: ask per file.
    if (!candidates.length) throw treeError;
  }
  const files = [];
  for (const path of candidates) {
    try {
      const res = await fetchJson('measures/component', { ...pr, component: `${key}:${path}`, metricKeys: COVERAGE_METRICS });
      const v = measuresOf(res.component);
      if (v.new_uncovered_lines > 0) files.push({ path, ...v });
    } catch {
      // file unknown to the analysis (not a source file): skip
    }
  }
  return files.sort(byCoverage).slice(0, MAX_FILES);
}

/** Collects what a failed gate needs explained for one project; null when the gate passed. */
export async function explainProject({ fetchJson, host, key, pullRequest, candidates = [] }) {
  const url = dashboardUrl(host, key, pullRequest);
  const pr = pullRequest ? { pullRequest } : {};
  let status;
  try {
    status = (await fetchJson('qualitygates/project_status', { ...pr, projectKey: key })).projectStatus;
  } catch (e) {
    return { key, url, error: e.message };
  }
  if (status.status === 'OK') return null;
  const report = { key, url, status: status.status, conditions: [], issues: [], issuesTotal: 0, files: [], notes: [] };
  report.conditions = (status.conditions ?? []).filter((c) => c.status === 'ERROR').map(describeCondition);
  try {
    const res = await fetchJson('issues/search', {
      ...pr, componentKeys: key, resolved: 'false', inNewCodePeriod: 'true',
      ps: String(MAX_ISSUES), s: 'SEVERITY', asc: 'false',
    });
    report.issuesTotal = res.total ?? res.issues.length;
    report.issues = res.issues.slice(0, MAX_ISSUES).map((i) => ({
      rule: i.rule, severity: i.severity, type: i.type, file: filePath(i.component, key), line: i.line ?? '?', message: i.message,
    }));
  } catch (e) {
    report.notes.push(`new issues unavailable: ${e.message}`);
  }
  if (report.conditions.some((c) => c.key === 'new_coverage' || c.key === 'new_line_coverage')) {
    try {
      report.files = await lowestCoverageFiles(fetchJson, key, pr, candidates);
    } catch (e) {
      report.notes.push(`uncovered files unavailable: ${e.message}`);
    }
  }
  return report;
}

const pad = (rows) => {
  const widths = rows[0].map((_, i) => Math.max(...rows.map((r) => String(r[i]).length)));
  return rows.map((r) => `  ${r.map((c, i) => String(c).padEnd(widths[i])).join('  ').trimEnd()}`);
};
const cell = (s) => String(s).replaceAll('|', '\\|').replaceAll('\n', ' ');
const mdTable = (head, rows) => [
  `| ${head.join(' | ')} |`,
  `|${head.map(() => '---').join('|')}|`,
  ...rows.map((r) => `| ${r.map(cell).join(' | ')} |`),
];

const conditionRow = (c) => [c.name, c.key, `${c.comparator} ${c.threshold}`, c.actual, c.newCode ? 'yes' : 'no'];
const issueRow = (i) => [i.rule, i.severity, i.type, `${i.file}:${i.line}`, i.message];
const fileRow = (f) => [f.path, f.new_coverage === undefined ? '?' : `${f.new_coverage}%`, f.new_uncovered_lines, f.new_lines_to_cover ?? '?'];
const COND_HEAD = ['Condition', 'Metric', 'Fails when value', 'Actual', 'New code'];
const ISSUE_HEAD = ['Rule', 'Severity', 'Type', 'File:line', 'Message'];
const FILE_HEAD = ['File', 'New-code coverage', 'Uncovered new lines', 'New lines to cover'];

export function markerLine(reports) {
  const parts = reports.map((r) => (r.error ? `${r.key}=details-unavailable` : `${r.key}=${r.conditions.map((c) => c.key).join(',') || r.status}`));
  return `SONAR-GATE-FAILED: ${parts.join(' ')}`;
}

export function renderText(reports) {
  if (!reports.length) return '';
  const out = [];
  for (const r of reports) {
    if (r.error) {
      out.push(`\n=== [${r.key}] quality gate details unavailable ===`, `  ${r.error}`, `  Open the analysis: ${r.url}`);
      continue;
    }
    out.push(`\n=== [${r.key}] quality gate ${r.status} ===`, 'Failed conditions:', ...pad([COND_HEAD, ...r.conditions.map(conditionRow)]));
    out.push(`New-code issues (${r.issues.length} of ${r.issuesTotal}, most severe first):`);
    out.push(...(r.issues.length ? pad([ISSUE_HEAD, ...r.issues.map(issueRow)]) : ['  none']));
    if (r.files.length) out.push(`Lowest new-code coverage (${r.files.length} files):`, ...pad([FILE_HEAD, ...r.files.map(fileRow)]));
    r.notes.forEach((n) => out.push(`  note: ${n}`));
    out.push(`Dashboard: ${r.url}`);
  }
  out.push('', markerLine(reports));
  return out.join('\n');
}

export function renderMarkdown(reports) {
  if (!reports.length) return '';
  const out = ['## SonarQube quality gate failed', ''];
  for (const r of reports) {
    out.push(`### ${r.key}`, '');
    if (r.error) {
      out.push(`Could not read the failure details: ${cell(r.error)}`, '', `Open the analysis: ${r.url}`, '');
      continue;
    }
    out.push(`Gate status: **${r.status}**`, '', ...mdTable(COND_HEAD, r.conditions.map(conditionRow)), '');
    out.push(`New-code issues (${r.issues.length} of ${r.issuesTotal}, most severe first):`, '');
    out.push(...(r.issues.length ? mdTable(ISSUE_HEAD, r.issues.map(issueRow)) : ['none']), '');
    if (r.files.length) out.push('Lowest new-code coverage:', '', ...mdTable(FILE_HEAD, r.files.map(fileRow)), '');
    r.notes.forEach((n) => out.push(`> note: ${cell(n)}`, ''));
    out.push(`[Dashboard](${r.url})`, '');
  }
  out.push(`\`${markerLine(reports)}\``, '');
  return out.join('\n');
}

export function makeFetchJson({ host, token, fetchImpl = fetch }) {
  const auth = `Basic ${Buffer.from(`${token}:`).toString('base64')}`;
  const mask = (s) => (token ? String(s).replaceAll(token, '***') : String(s));
  return async (path, params) => {
    let res;
    try {
      res = await fetchImpl(`${host}/api/${path}?${new URLSearchParams(params)}`, { headers: { Authorization: auth } });
    } catch (e) {
      throw new Error(mask(`GET /api/${path} failed: ${e.cause?.code ?? e.message}`));
    }
    if (!res.ok) throw new Error(`GET /api/${path} -> HTTP ${res.status}`);
    return res.json();
  };
}

async function main(argv, env) {
  const host = (env.SONAR_HOST_URL || '').replace(/\/$/, '');
  const fetchJson = makeFetchJson({ host, token: env.SONAR_TOKEN || '' });
  const listing = env.CANDIDATE_FILES
    ? readFileSync(env.CANDIDATE_FILES, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean)
    : [];
  const reports = [];
  for (const arg of argv) {
    const [key, dir] = arg.split(':');
    const candidates = dir ? listing.filter((f) => f.startsWith(`${dir}/`)).map((f) => f.slice(dir.length + 1)) : [];
    const report = await explainProject({ fetchJson, host, key, pullRequest: env.SONAR_PULL_REQUEST, candidates });
    if (report) reports.push(report);
  }
  const text = renderText(reports);
  if (text) console.log(text);
  if (reports.length && env.GITHUB_STEP_SUMMARY) appendFileSync(env.GITHUB_STEP_SUMMARY, `${renderMarkdown(reports)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main(process.argv.slice(2), process.env);
}
