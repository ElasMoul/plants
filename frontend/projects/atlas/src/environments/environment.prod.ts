/**
 * Atlas production environment. Both frontends are served same-origin behind one
 * nginx, so the API is a relative path and the classic app is reached by relative
 * path too (empty base → same origin). Same-origin is also what makes the shared
 * JWT session work: localStorage is per-origin.
 */
export const environment = {
  production: true,
  apiUrl: '/api/v1',
  classicAppUrl: '',
};
