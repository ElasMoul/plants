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
