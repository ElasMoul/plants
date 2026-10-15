import { RenderMode, ServerRoute } from '@angular/ssr';
import { CHILD_ONLY_PREFIXES, CLIENT_ROUTE_PREFIXES } from './client-routes';
import { PUBLIC_LANGUAGES } from './shared/i18n/public-routes';

/**
 * Only the public language landing pages (/en/, /fr/) are prerendered. Every other route —
 * guarded, auth and unknown — is client-rendered, so authenticated pages are never rendered on
 * the server and no per-user data can leak into static HTML.
 */
export const serverRoutes: ServerRoute[] = [
  // Listed before ':lang' so the language param never claims a guarded or auth prefix.
  { path: '', renderMode: RenderMode.Client },
  ...CLIENT_ROUTE_PREFIXES.flatMap((prefix): ServerRoute[] => {
    const children: ServerRoute = { path: `${prefix}/**`, renderMode: RenderMode.Client };
    return CHILD_ONLY_PREFIXES.includes(prefix)
      ? [children]
      : [{ path: prefix, renderMode: RenderMode.Client }, children];
  }),
  {
    path: ':lang',
    renderMode: RenderMode.Prerender,
    getPrerenderParams: async () => PUBLIC_LANGUAGES.map(lang => ({ lang })),
  },
  { path: '**', renderMode: RenderMode.Client },
];
