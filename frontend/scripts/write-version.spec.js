const { spawnSync } = require("node:child_process");
const { mkdtempSync, readFileSync } = require("node:fs");
const { tmpdir } = require("node:os");
const { join } = require("node:path");

const A = "a".repeat(40);
const B = "b".repeat(40);

function run(env) {
  const dir = mkdtempSync(join(tmpdir(), "ver-"));
  const clean = { ...process.env, APP_REVISION: "", GITHUB_SHA: "", ...env };
  const r = spawnSync("node", [join(__dirname, "write-version.mjs"), dir], {
    env: clean,
  });
  expect(r.status).toBe(0);
  return JSON.parse(readFileSync(join(dir, "version.json"), "utf8"));
}

describe("write-version.mjs", () => {
  it("writes a valid APP_REVISION", () => {
    expect(run({ APP_REVISION: A }).revision).toBe(A);
  });
  it("prefers APP_REVISION over GITHUB_SHA", () => {
    expect(run({ APP_REVISION: A, GITHUB_SHA: B }).revision).toBe(A);
  });
  it("falls back to GITHUB_SHA when APP_REVISION is unset", () => {
    expect(run({ GITHUB_SHA: B }).revision).toBe(B);
  });
  it("writes null for missing, short, uppercase or non-hex values", () => {
    expect(run({}).revision).toBeNull();
    expect(run({ APP_REVISION: "abc123" }).revision).toBeNull();
    expect(run({ APP_REVISION: A.toUpperCase() }).revision).toBeNull();
    expect(run({ APP_REVISION: "g".repeat(40) }).revision).toBeNull();
  });
});
