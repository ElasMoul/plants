import { WorldNode } from './world.model';

/**
 * Deep-link a world node to its page in the classic PlantPal app. The atlas world
 * is the primary UI, but every record still has a classic page; this is the "Open
 * in PlantPal" bridge. Returns null for nodes with no classic counterpart.
 */
export function classicLinkFor(node: Pick<WorldNode, 'id'>, base: string): string | null {
  const at = (path: string) => `${base}${path}`;

  const plant = /^n-plant-(\d+)$/.exec(node.id);
  if (plant) return at(`/plants/${plant[1]}`);
  if (/^n-species-\d+$/.test(node.id)) return at('/garden');

  const treatment = /^n-treatment-(\d+)$/.exec(node.id);
  if (treatment) return at(`/treatment/${treatment[1]}`);
  const scan = /^n-scan-(\d+)$/.exec(node.id);
  if (scan) return at(`/identify/${scan[1]}`);
  if (/^n-log-\d+$/.test(node.id)) return at('/plants');

  switch (node.id) {
    case 'n-garden':
    case 'n-garden-more':
      return at('/plants');
    case 'n-species':
    case 'n-species-more':
      return at('/garden');
    case 'n-reminders':
    case 'n-care':
    case 'n-journal':
    case 'n-journal-more':
    case 'n-treatments':
    case 'n-treatments-more':
      return at('/reminders');
    case 'n-ident':
    case 'n-scans-more':
      return at('/identify');
    case 'n-ask':
      return at('/chat');
    case 'n-today':
    case 'n-account':
      return at('/home');
    case 'n-treatment':
      return at('/treatment');
    default:
      return null;
  }
}

/** The classic app's login page — where atlas sends an unauthenticated visitor. */
export function classicLoginLink(base: string): string {
  return `${base}/login`;
}

/**
 * When this session began and when it lapses, read from the JWT the classic app
 * issued. Purely local: a malformed or non-JWT token yields nothing rather than a
 * guess, and nothing here validates the signature — the server does that.
 */
export function sessionTimes(
  token: string | null | undefined,
): { issuedAt?: string; expiresAt?: string } | undefined {
  const part = token?.split('.')[1];
  if (!part) return undefined;
  try {
    const padded = (part + '='.repeat((4 - (part.length % 4)) % 4))
      .replace(/-/g, '+')
      .replace(/_/g, '/');
    const claims = JSON.parse(atob(padded)) as { iat?: number; exp?: number };
    const out: { issuedAt?: string; expiresAt?: string } = {};
    if (typeof claims.iat === 'number') out.issuedAt = new Date(claims.iat * 1000).toISOString();
    if (typeof claims.exp === 'number') out.expiresAt = new Date(claims.exp * 1000).toISOString();
    return out.issuedAt || out.expiresAt ? out : undefined;
  } catch {
    return undefined;
  }
}
