export const environment = {
  production: true,
  apiUrl: '/api/v1',
  // Must match the backend's VAPID_PUBLIC_KEY (app.web-push.public-key)
  vapidPublicKey: '',
  // Set SENTRY_DSN in CI/CD environment secrets
  sentryDsn: '',
};
