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

  switch (node.id) {
    case 'n-garden':
    case 'n-garden-more':
      return at('/plants');
    case 'n-species':
    case 'n-species-more':
      return at('/garden');
    case 'n-reminders':
      return at('/reminders');
    case 'n-ident':
      return at('/identify');
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
