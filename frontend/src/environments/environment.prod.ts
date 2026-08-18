export const environment = {
  production: true,
  apiUrl: '/api/v1',
  // Must match the backend's VAPID_PUBLIC_KEY (app.web-push.public-key)
  vapidPublicKey: '',
  // Set SENTRY_DSN in CI/CD environment secrets
  sentryDsn: '',
  // Where the Atlas (Rhizome) frontend is served — the login page's "Atlas"
  // checkbox redirects here after sign-in. Matches docker-compose's
  // frontend-atlas service (8445:443); adjust if the deploy moves it.
  atlasUrl: 'https://localhost:8445',
};
