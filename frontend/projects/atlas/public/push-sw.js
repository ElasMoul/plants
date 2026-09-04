/**
 * The push knock. It shows what the server sent and, when pressed, opens the
 * atlas — it never decides where you land beyond the front door.
 */
self.addEventListener('push', e => {
  let d = {};
  try {
    d = e.data ? e.data.json() : {};
  } catch {
    d = {};
  }
  e.waitUntil(
    self.registration.showNotification(d.title || 'PlantPal', {
      body: d.body || 'Something in your garden is due.',
    }),
  );
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(self.clients.openWindow('/'));
});
