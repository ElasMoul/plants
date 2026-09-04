/**
 * Atlas dev environment. apiUrl is proxied to the backend (:8180) by
 * proxy.conf.json; classicAppUrl points at the classic PlantPal frontend (:4200)
 * for interop deep-links and sign-in. In prod both apps are served same-origin
 * (see environment.prod.ts), so classicAppUrl is relative.
 */
export const environment = {
  production: false,
  apiUrl: '/api/v1',
  // must equal the backend's app.web-push.public-key; empty ⇒ push refused in words
  vapidPublicKey: '',
  mockByDefault: false,
  classicAppUrl: 'http://localhost:4200',
};
