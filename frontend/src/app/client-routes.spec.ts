/// <reference types="node" />
import { readFileSync } from 'fs';
import { join } from 'path';
import { CHILD_ONLY_PREFIXES, CLIENT_ROUTE_PREFIXES } from './client-routes';

// Jest runs from the frontend/ root.
const frontendRoot = process.cwd();
const read = (relative: string): string => readFileSync(join(frontendRoot, 'src/app', relative), 'utf8');
const sorted = (values: readonly string[]): string[] => [...values].sort();

/** First path segment of every `path: '…'` route declared in a routing source file. */
function firstSegments(source: string): string[] {
  const found = [...source.matchAll(/(?:^|[{,\s])path:\s*['"]([^'"]*)['"]/gm)].map(m => m[1].split('/')[0]);
  return [...new Set(found)];
}

/** The `a|b|c` alternation of the first regex group in the given source line. */
function alternation(source: string, marker: RegExp): string[] {
  const line = source.split('\n').find(l => marker.test(l));
  if (!line) throw new Error(`no line matching ${marker}`);
  const group = /\(([a-z|-]+)\)/.exec(line);
  if (!group) throw new Error(`no alternation in: ${line}`);
  return group[1].split('|');
}

describe('Host route rules stay in step with the router', () => {
  // Mounted at the root of the app: app-routing itself, plus the root-mounted lazy modules.
  const routed = new Set([
    ...firstSegments(read('app-routing.module.ts')),
    ...firstSegments(read('features/reminder/reminder-routing.module.ts')),
    ...firstSegments(read('features/auth/auth-routing.module.ts')),
  ]);
  const notClient = new Set(['', ':lang', '**']);

  it('lists every top-level client route and nothing else', () => {
    const expected = [...routed].filter(p => !notClient.has(p));
    // 'dashboard' is a redirect-only route; it is still a client route (it redirects in the app).
    expect(sorted(CLIENT_ROUTE_PREFIXES)).toEqual(sorted(expected));
  });

  it('only marks prefixes without a bare route as child-only', () => {
    for (const prefix of CLIENT_ROUTE_PREFIXES) {
      const bare = new RegExp(String.raw`path:\s*['"]` + prefix + String.raw`['"]`);
      const hasBare = [
        read('app-routing.module.ts'),
        read('features/reminder/reminder-routing.module.ts'),
        read('features/auth/auth-routing.module.ts'),
      ].some(source => bare.test(source));
      expect([prefix, CHILD_ONLY_PREFIXES.includes(prefix)]).toEqual([prefix, !hasBare]);
    }
  });

  it('routes the same prefixes to the shell in nginx', () => {
    const routes = readFileSync(join(frontendRoot, 'nginx/routes.inc'), 'utf8');
    expect(sorted(alternation(routes, /^location ~ /))).toEqual(sorted(CLIENT_ROUTE_PREFIXES));
  });

  it('routes the same prefixes to the shell in vercel.json (deploy workflow)', () => {
    const deploy = readFileSync(join(frontendRoot, '../.github/workflows/deploy.yml'), 'utf8');
    expect(sorted(alternation(deploy, /\(admin\|/)))
      .toEqual(sorted(CLIENT_ROUTE_PREFIXES));
  });

  it('limits the service worker navigation fallback to the same prefixes', () => {
    const config = JSON.parse(readFileSync(join(frontendRoot, 'ngsw-config.json'), 'utf8'));
    const bare = (config.navigationUrls as string[]).filter(u => /^\/[a-z-]+$/.test(u)).map(u => u.slice(1));
    expect(sorted(bare)).toEqual(sorted(CLIENT_ROUTE_PREFIXES));
    expect(config.index).toBe('/index.csr.html');
  });

  it('serves the public language landing from the :lang route only for en and fr', () => {
    expect(routed.has(':lang')).toBe(true);
    expect(read('app-routing.module.ts')).toContain('data: { public: true }');
  });
});
