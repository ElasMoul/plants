// Writes version.json ({"revision": <40-hex sha | null>}) into the built dist directory so a
// deployed frontend can report the commit it was built from (PLA-50). APP_REVISION wins over
// GITHUB_SHA; anything that is not a full lowercase SHA is reported as null, never defaulted.
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const FULL_SHA = /^[0-9a-f]{40}$/;
const dir = process.argv[2];
if (!dir) {
  console.error("usage: write-version.mjs <dist-dir>");
  process.exit(1);
}
const pick = (v) =>
  typeof v === "string" && FULL_SHA.test(v.trim()) ? v.trim() : null;
const revision = pick(process.env.APP_REVISION) ?? pick(process.env.GITHUB_SHA);
mkdirSync(dir, { recursive: true });
writeFileSync(join(dir, "version.json"), JSON.stringify({ revision }) + "\n");
