/**
 * The tokens pin guard (F). The vendored tokens.css must stay a byte-identical
 * copy of the Rhizome source (frontend-atlas/theme-a/tokens.css) — it is a pin,
 * not a fork. A local edit fails here; the fix is to re-pin frontend-atlas and
 * re-copy, never to patch this file (see the sibling README).
 */

// Node globals — declared locally because the atlas tsconfig excludes @types/node.
declare const require: (m: string) => { readFileSync: (p: string, e: string) => string; join: (...p: string[]) => string };
declare const __dirname: string;

describe('Rhizome tokens pin guard', () => {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  const norm = (s: string) => s.replace(/\r\n/g, '\n');

  it('vendored tokens.css matches the pinned frontend-atlas source', () => {
    const vendored = readFileSync(join(__dirname, 'tokens.css'), 'utf8');
    const source = readFileSync(join(__dirname, '../../../../../frontend-atlas/theme-a/tokens.css'), 'utf8');
    expect(norm(vendored)).toBe(norm(source));
  });
});
