export const environment = {
  production: true,
  apiUrl: '/api/v1',
  // Must match the backend's VAPID_PUBLIC_KEY (app.web-push.public-key)
  vapidPublicKey: '',
  // Set SENTRY_DSN in CI/CD environment secrets
  sentryDsn: '',
  // Vercel Web Analytics — deploy.yml flips this to true for the Vercel build only;
  // Docker/nginx builds have no /_vercel/insights endpoint, so it stays off there.
  vercelAnalytics: false,
};
