export const environment = {
  production: false,
  apiUrl: '/api/v1',
  // Must match the backend's VAPID_PUBLIC_KEY (app.web-push.public-key)
  vapidPublicKey: '',
  // Sentry DSN — empty string disables Sentry (default in dev)
  sentryDsn: '',
  // Where the Atlas (Rhizome) frontend is served — the login page's "Atlas"
  // checkbox redirects here after sign-in (dev: ng serve atlas).
  atlasUrl: 'http://localhost:4300',
};
