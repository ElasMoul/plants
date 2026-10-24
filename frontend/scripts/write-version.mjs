// Stamps <dist>/version.json with the commit this frontend was built from (PLA-50).
// Usage: node scripts/write-version.mjs <dist-dir>
// Source: APP_REVISION, else GITHUB_SHA. Only a full 40-char lowercase hex SHA is accepted;
// anything else is written as null ("not reported"), never a default.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const FULL_SHA = /^[0-9a-f]{40}$/;

export function resolveRevision(env = process.env) {
  const raw = env.APP_REVISION?.trim() || env.GITHUB_SHA?.trim() || "";
  return FULL_SHA.test(raw) ? raw : null;
}

export function writeVersion(distDir, env = process.env) {
  const revision = resolveRevision(env);
  mkdirSync(distDir, { recursive: true });
  writeFileSync(
    join(distDir, "version.json"),
    JSON.stringify({ revision }) + "\n",
  );
  return revision;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const distDir = process.argv[2];
  if (!distDir) {
    console.error("usage: node scripts/write-version.mjs <dist-dir>");
    process.exit(2);
  }
  const revision = writeVersion(distDir);
  console.log(
    `version.json -> ${distDir}: ${revision ?? "null (no valid APP_REVISION/GITHUB_SHA)"}`,
  );
}
