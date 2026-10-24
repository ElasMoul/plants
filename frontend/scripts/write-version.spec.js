// Plain CommonJS: the Angular spec tsconfig carries no node typings.
const { execFileSync } = require("node:child_process");
const { mkdtempSync, readFileSync, rmSync } = require("node:fs");
const { tmpdir } = require("node:os");
const { join } = require("node:path");

const SHA = "0123456789abcdef0123456789abcdef01234567";
const OTHER = "fedcba9876543210fedcba9876543210fedcba98";
const SCRIPT = join(__dirname, "write-version.mjs");

function stamp(env) {
  const dir = mkdtempSync(join(tmpdir(), "write-version-"));
  try {
    // A clean env: a developer's own APP_REVISION/GITHUB_SHA must not leak in.
    execFileSync(process.execPath, [SCRIPT, dir], {
      env: { PATH: process.env.PATH ?? "", ...env },
      stdio: "pipe",
    });
    return JSON.parse(readFileSync(join(dir, "version.json"), "utf8"));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

describe("write-version.mjs", () => {
  it("writes a valid APP_REVISION", () => {
    expect(stamp({ APP_REVISION: SHA })).toEqual({ revision: SHA });
  });

  it("falls back to GITHUB_SHA when APP_REVISION is unset", () => {
    expect(stamp({ GITHUB_SHA: OTHER })).toEqual({ revision: OTHER });
  });

  it("prefers APP_REVISION over GITHUB_SHA", () => {
    expect(stamp({ APP_REVISION: SHA, GITHUB_SHA: OTHER })).toEqual({
      revision: SHA,
    });
  });

  it.each([
    ["unset", {}],
    ["empty", { APP_REVISION: "" }],
    ["short", { APP_REVISION: "abc123" }],
    ["uppercase", { APP_REVISION: SHA.toUpperCase() }],
    ["non-hex", { APP_REVISION: "z".repeat(40) }],
    ["too long", { APP_REVISION: SHA + "0" }],
  ])("writes null when %s", (_name, env) => {
    expect(stamp(env)).toEqual({ revision: null });
  });

  it("does not fall back to GITHUB_SHA when APP_REVISION is set but malformed", () => {
    expect(stamp({ APP_REVISION: "abc", GITHUB_SHA: OTHER })).toEqual({
      revision: null,
    });
  });
});
