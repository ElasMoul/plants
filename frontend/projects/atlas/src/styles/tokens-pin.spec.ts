/**
 * The tokens pin guard. Atlas serves the Herbarium design pack (plantpal-e8e4b3e5 1.1.0):
 * frontend/design-pack/tokens.css is the pack's tokens.css and
 * frontend/design-pack/atlas-theme.css is the accepted theme-b, both byte-identical pins.
 * The sha256 pins below hold in every checkout (this product repo is built without the
 * hexagon); when the hexagon's design/ is reachable the files are also compared to it.
 */

// Node globals — declared locally because the atlas tsconfig excludes @types/node.
declare const require: (m: string) => any;
declare const __dirname: string;

describe('Herbarium tokens pin guard', () => {
  const fs = require('fs');
  const { join } = require('path');
  const { createHash } = require('crypto');
  const norm = (s: string) => s.replace(/\r\n/g, '\n');
  const sha = (s: string) => createHash('sha256').update(norm(s)).digest('hex');
  const frontend = join(__dirname, '../../../..');
  const pack = join(frontend, 'design-pack');
  const hexagon = join(frontend, '../..');
  const TOKENS_SHA = '6a8feb714156b6891918b845111b1a6de23ebd01ed3a6d00ff10586ba7b1bf78';
  const THEME_SHA = '4e343b8a23e31f92794accf8a83c6fe8b9e411a27380e081c86438d3896e41cb';

  it('serves the pack tokens.css and the theme copy unchanged', () => {
    expect(sha(fs.readFileSync(join(pack, 'tokens.css'), 'utf8'))).toBe(TOKENS_SHA);
    expect(sha(fs.readFileSync(join(pack, 'atlas-theme.css'), 'utf8'))).toBe(THEME_SHA);
  });

  it('matches the hexagon design pack when it is present', () => {
    const src = join(hexagon, 'design');
    if (!fs.existsSync(join(src, 'tokens.css'))) return;
    expect(norm(fs.readFileSync(join(pack, 'tokens.css'), 'utf8'))).toBe(norm(fs.readFileSync(join(src, 'tokens.css'), 'utf8')));
    expect(norm(fs.readFileSync(join(pack, 'atlas-theme.css'), 'utf8'))).toBe(
      norm(fs.readFileSync(join(src, 'reference/themes/atlas/tokens.css'), 'utf8')),
    );
  });

  it('loads the pins from angular.json ahead of rhizome.css and no longer the round-8 file', () => {
    const cfg = fs.readFileSync(join(frontend, 'angular.json'), 'utf8');
    const i = cfg.indexOf('design-pack/tokens.css');
    expect(i).toBeGreaterThan(-1);
    expect(cfg.indexOf('design-pack/atlas-theme.css')).toBeGreaterThan(i);
    expect(cfg.indexOf('projects/atlas/src/styles/rhizome.css')).toBeGreaterThan(i);
    expect(cfg).not.toContain('atlas/src/styles/tokens.css');
    expect(fs.existsSync(join(__dirname, 'tokens.css'))).toBe(false);
  });
});

export {};
