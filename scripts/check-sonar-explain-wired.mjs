// Guard: the sonar-gate failure explanation must stay wired in. A revert of an unrelated merge once
// dropped it silently (PR #214 reverted #212, which had carried it into main); this check fails the
// CI-tool job when the script, its tests, or the workflow steps that run them are gone.
// Run from the repo root: node scripts/check-sonar-explain-wired.mjs [workflow path]
import { existsSync, readFileSync } from 'node:fs';

const workflowPath = process.argv[2] ?? '.github/workflows/ci.yml';
const problems = [];
const need = (ok, message) => { if (!ok) problems.push(message); };

for (const file of ['scripts/sonar-explain-failure.mjs', 'scripts/sonar-explain-failure.test.mjs', 'scripts/check-sonar-coverage-exclusions.mjs']) {
  need(existsSync(file), `missing file: ${file}`);
}
need(existsSync('scripts/sonar-fixtures') , 'missing directory: scripts/sonar-fixtures');

const workflow = readFileSync(workflowPath, 'utf8');
const jobBlock = (name) => workflow.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [A-Za-z0-9_-]+:\\n|(?![\\s\\S]))`, 'm'))?.[1] ?? '';
const stepBlock = (job, name) => job.match(new RegExp(`^      - name: ${name}\\n([\\s\\S]*?)(?=^      - |(?![\\s\\S]))`, 'm'))?.[1];

const gate = jobBlock('sonar-gate');
need(gate !== '', `${workflowPath}: job sonar-gate not found`);
need(/^    concurrency:\s+group: sonar-gate\s+cancel-in-progress: false\s*$/m.test(gate), 'sonar-gate: the repository-wide concurrency group (group: sonar-gate, cancel-in-progress: false) is missing');
const explain = stepBlock(gate, 'Explain quality gate failure');
need(explain !== undefined, `${workflowPath}: sonar-gate has no step "Explain quality gate failure"`);
if (explain !== undefined) {
  need(/node scripts\/sonar-explain-failure\.mjs/.test(explain), 'the explain step does not run scripts/sonar-explain-failure.mjs');
  need(/EXPECTED_REVISIONS:/.test(explain), 'the explain step does not pass EXPECTED_REVISIONS (the revision check)');
  need(/^        if: .*(failure\(\)|always\(\)|outcome)/m.test(explain), 'the explain step must run on failure (if: failure() / always() / step outcome)');
  need(!/^        if: .*success\(\)/m.test(explain), 'the explain step must not be gated on success()');
}
const scanSteps = [...gate.matchAll(/^      - name: (Scan .*)$/gm)].map((m) => m[1]);
need(scanSteps.length >= 2, 'sonar-gate: expected the backend and frontend scan steps');
need(!explain || gate.indexOf('- name: Explain quality gate failure') > gate.lastIndexOf(`- name: ${scanSteps.at(-1)}`), 'the explain step must come after the scan steps');

need(/node scripts\/check-sonar-coverage-exclusions\.mjs/.test(jobBlock('frontend-ci')), 'frontend-ci does not run the Sonar coverage-exclusions drift check');
const toolJob = jobBlock('dev-delivery-tool-ci');
need(/node --test scripts\/sonar-explain-failure\.test\.mjs/.test(toolJob), 'dev-delivery-tool-ci does not run the explain script tests');
need(/node scripts\/check-sonar-explain-wired\.mjs/.test(toolJob), 'dev-delivery-tool-ci does not run this guard');

if (problems.length) {
  console.error('sonar-gate failure explanation is not fully wired in:');
  problems.forEach((p) => console.error(`  - ${p}`));
  process.exit(1);
}
console.log('sonar-gate failure explanation is wired in (script, tests, workflow step, drift check)');
