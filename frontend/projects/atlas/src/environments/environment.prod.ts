/**
 * Atlas production environment. The API is same-origin (this nginx proxies
 * /api → backend), but the classic app lives on its OWN origin in the compose
 * deploy (frontend service, 8444:443) — so sign-in and "Open in PlantPal" links
 * must be absolute. Sessions cross the origin gap via the login handoff
 * fragment (shared-core session-handoff), not via shared localStorage.
 */
export const environment = {
  production: true,
  apiUrl: '/api/v1',
  // must equal the backend's app.web-push.public-key; empty ⇒ push refused in words
  vapidPublicKey: '',
  mockByDefault: false,
  classicAppUrl: 'https://localhost:8444',
};
