/**
 * Top-level paths served by the client-only app shell (index.csr.html): every authenticated
 * route plus login/register. They are never prerendered. The host rules (frontend/nginx/routes.inc
 * and the vercel.json in .github/workflows/deploy.yml) must list exactly these; client-routes.spec.ts
 * fails when the router, this list and the host rules drift apart.
 */
export const CLIENT_ROUTE_PREFIXES: readonly string[] = [
  'admin',
  'chat',
  'dashboard',
  'garden',
  'home',
  'identify',
  'login',
  'plans',
  'plants',
  'preferences',
  'register',
  'reminders',
  'treatment',
  'treatment-plans',
  'voice-test',
];

/** Prefixes with no route of their own (only `<prefix>/:id…` children), so no bare server route. */
export const CHILD_ONLY_PREFIXES: readonly string[] = ['plans', 'treatment-plans'];
