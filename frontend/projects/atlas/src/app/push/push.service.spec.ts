import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { environment } from '../../environments/environment';
import { MOCK_MODE, provideMockModeOff } from '../core/mock-mode';
import { DeviceStore } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import { WorldStore } from '../world/world.store';
import { PushService } from './push.service';

const ok = (data: unknown = undefined) => ({ success: true, message: '', timestamp: '', data });

/** A browser that can receive knocks: a service worker, a PushManager, permission. */
function browserWithPush(permission: NotificationPermission, endpoint = 'https://push.example/abc') {
  const subscription = {
    endpoint,
    toJSON: () => ({ endpoint, keys: { p256dh: 'p256dh-key', auth: 'auth-key' } }),
    unsubscribe: () => Promise.resolve(true),
  };
  const registration = {
    pushManager: {
      subscribe: jest.fn(() => Promise.resolve(subscription)),
      getSubscription: () => Promise.resolve(subscription),
    },
  };
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      register: jest.fn(() => Promise.resolve(registration)),
      getRegistration: () => Promise.resolve(registration),
    },
  });
  (globalThis as unknown as { PushManager: unknown }).PushManager = class {};
  (globalThis as unknown as { Notification: unknown }).Notification = {
    permission,
    requestPermission: () => Promise.resolve(permission),
  };
  return registration;
}

function noPushBrowser(): void {
  const nav = navigator as unknown as Record<string, unknown>;
  delete nav['serviceWorker'];
  delete (globalThis as unknown as Record<string, unknown>)['PushManager'];
  delete (globalThis as unknown as Record<string, unknown>)['Notification'];
}

describe('PushService (S6 — the knock, and its honest refusals)', () => {
  let push: PushService;
  let device: DeviceStore;
  let settings: SettingsStore;
  let store: WorldStore;
  let http: HttpTestingController;

  function configure(mock = false): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
        mock
          ? { provide: MOCK_MODE, useValue: { enabled: true, scenario: 'garden', latencyMs: 0 } }
          : provideMockModeOff(),
      ],
    });
    push = TestBed.inject(PushService);
    device = TestBed.inject(DeviceStore);
    settings = TestBed.inject(SettingsStore);
    store = TestBed.inject(WorldStore);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => {
    localStorage.clear();
    environment.vapidPublicKey = '';
    noPushBrowser();
  });

  afterEach(() => {
    http.verify();
    environment.vapidPublicKey = '';
    localStorage.clear();
    noPushBrowser();
  });

  it('reports unsupported when the browser has no service worker', () => {
    configure();
    expect(push.state()).toBe('unsupported');
  });

  it('refuses in words when the browser cannot receive pushes, and stays off', async () => {
    configure();
    settings.set('notifications.push', 'on');
    await new Promise<void>(done => push.enable().subscribe(() => done()));
    expect(store.announcement()).toBe('This browser cannot receive pushes.');
    expect(settings.get('notifications.push')).toBe('off');
  });

  it('reports unconfigured with an empty server key and makes no request', async () => {
    browserWithPush('granted');
    configure();
    expect(push.state()).toBe('unconfigured');
    await new Promise<void>(done => push.enable().subscribe(() => done()));
    expect(store.announcement()).toContain("Push needs this server's public key");
    expect(settings.get('notifications.push')).toBe('off');
  });

  it('reports blocked when the reader already said no', () => {
    browserWithPush('denied');
    environment.vapidPublicKey = 'BOsomekeyAAA';
    configure();
    expect(push.state()).toBe('blocked');
  });

  it('refuses in words when the configured key cannot be read', async () => {
    browserWithPush('granted');
    environment.vapidPublicKey = 'not base64!!!';
    configure();
    await new Promise<void>(done => push.enable().subscribe(() => done()));
    expect(store.announcement()).toContain('could not be read');
    expect(settings.get('notifications.push')).toBe('off');
  });

  it('a denied permission refuses in words rather than subscribing', async () => {
    browserWithPush('denied');
    environment.vapidPublicKey = 'BOsomekeyAAA';
    configure();
    await new Promise<void>(done => push.enable().subscribe(() => done()));
    expect(store.announcement()).toBe('Push stays off — you did not allow it.');
    expect(device.state().push).toBeUndefined();
  });

  it('a granted permission posts the endpoint and both keys, and remembers the device', async () => {
    const reg = browserWithPush('granted');
    environment.vapidPublicKey = 'BOsomekeyAAA';
    configure();
    const done = new Promise<void>(resolve => push.enable().subscribe(() => resolve()));
    await new Promise(r => setTimeout(r, 0));
    const req = http.expectOne('/api/v1/notifications/subscribe');
    expect(req.request.body).toEqual({
      endpoint: 'https://push.example/abc',
      keyP256dh: 'p256dh-key',
      keyAuth: 'auth-key',
    });
    req.flush(ok());
    await done;
    expect(reg.pushManager.subscribe).toHaveBeenCalled();
    expect(device.state().push?.endpoint).toBe('https://push.example/abc');
    expect(settings.get('notifications.push')).toBe('on');
    expect(push.state()).toBe('on');
  });

  it('in the mock garden it posts the mock subscription without a browser flow', async () => {
    configure(true);
    const done = new Promise<void>(resolve => push.enable().subscribe(() => resolve()));
    const req = http.expectOne('/api/v1/notifications/subscribe');
    expect(req.request.body).toEqual({
      endpoint: 'mock://device',
      keyP256dh: 'mock',
      keyAuth: 'mock',
    });
    req.flush(ok());
    await done;
    expect(push.state()).toBe('on');
  });

  it('a remembered endpoint never subscribes twice', async () => {
    configure(true);
    device.setPush({ endpoint: 'mock://device', subscribedAt: new Date().toISOString() });
    await new Promise<void>(done => push.enable().subscribe(() => done()));
    http.expectNone('/api/v1/notifications/subscribe');
    expect(store.announcement()).toBe('Push is already on for this device.');
  });

  it('disabling forgets the device and says what the server still holds', async () => {
    configure(true);
    device.setPush({ endpoint: 'mock://device', subscribedAt: new Date().toISOString() });
    await new Promise<void>(done => push.disable().subscribe(() => done()));
    expect(device.state().push).toBeUndefined();
    expect(settings.get('notifications.push')).toBe('off');
    expect(store.announcement()).toContain('Push is off on this device');
  });
});
