// Fails when sonar.coverage.exclusions drifts from the coverage tools' own excludes:
//   backend  = JaCoCo <excludes> in backend/pom.xml
//   frontend = the '!' entries of collectCoverageFrom in frontend/jest.config.js
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const read = (p) => readFileSync(new URL(`../${p}`, import.meta.url), 'utf8');
const sorted = (s) => [...new Set(s)].sort();

function diff(label, expected, actual) {
  const missing = expected.filter((x) => !actual.includes(x));
  const extra = actual.filter((x) => !expected.includes(x));
  if (!missing.length && !extra.length) return 0;
  console.error(`${label}: sonar.coverage.exclusions drifted from the coverage tool's excludes`);
  missing.forEach((x) => console.error(`  missing in Sonar: ${x}`));
  extra.forEach((x) => console.error(`  not excluded by coverage tool: ${x}`));
  return 1;
}

// backend
const pom = read('backend/pom.xml');
const sonarBackend = pom.match(/<sonar\.coverage\.exclusions>([^<]*)</)?.[1].split(',').map((s) => s.trim());
const jacoco = pom.match(/<artifactId>jacoco-maven-plugin<\/artifactId>[\s\S]*?<excludes>([\s\S]*?)<\/excludes>/)?.[1];
if (!sonarBackend || !jacoco) throw new Error('backend: could not find sonar.coverage.exclusions or the JaCoCo excludes in pom.xml');
const jacocoAsSonar = [...jacoco.matchAll(/<exclude>([^<]+)<\/exclude>/g)].map(([, p]) => {
  const rel = p.trim().replace(/^com\/plantpal\//, '').replace(/\.class$/, '.java');
  return rel.startsWith('**/') ? rel : `**/${rel}`;
});

// frontend
const props = read('frontend/sonar-project.properties');
const sonarFrontend = props.match(/^sonar\.coverage\.exclusions=(.*)$/m)?.[1].split(',').map((s) => s.trim());
if (!sonarFrontend) throw new Error('frontend: sonar.coverage.exclusions is missing from sonar-project.properties');
const jest = createRequire(import.meta.url)(new URL('../frontend/jest.config.js', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const jestExcludes = jest.collectCoverageFrom.filter((p) => p.startsWith('!')).map((p) => p.slice(1));

const failed = diff('backend', sorted(jacocoAsSonar), sorted(sonarBackend)) + diff('frontend', sorted(jestExcludes), sorted(sonarFrontend));
if (failed) process.exit(1);
console.log('sonar.coverage.exclusions match the coverage tools (backend and frontend)');
