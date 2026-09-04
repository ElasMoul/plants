import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { provideMockModeOff } from '../core/mock-mode';
import { PREFS_CACHE_KEY, PreferencesClient } from './preferences.client';
import { SettingsStore } from './settings.store';

const ok = <T>(data: T) => ({ success: true, message: '', timestamp: '', data });

const SERVER = {
  aiModelPreference: 'GITHUB_GPT4O',
  visionModelPreference: 'GITHUB_GPT4O',
  reasoningModelPreference: 'DEEPSEEK_R1',
  plantnetProject: 'all',
  plantnetLang: 'en',
  businessTier: false,
};

describe('PreferencesClient (S6 — the server-backed five)', () => {
  let client: PreferencesClient;
  let settings: SettingsStore;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMockModeOff(),
        ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
      ],
    });
    client = TestBed.inject(PreferencesClient);
    settings = TestBed.inject(SettingsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('reads from the server and never from the classic session cache', () => {
    sessionStorage.setItem(
      PREFS_CACHE_KEY,
      JSON.stringify({ ...SERVER, visionModelPreference: 'PLANTNET' }),
    );
    let got: unknown;
    client.read().subscribe(p => (got = p));
    const req = http.expectOne('/api/v1/users/me/preferences');
    expect(req.request.method).toBe('GET');
    req.flush(ok(SERVER));
    expect(got).toEqual(SERVER);
    expect(settings.serverPrefs()!.visionModelPreference).toBe('GITHUB_GPT4O');
    expect(settings.prefsState()).toBe('idle');
  });

  it('says so when the read did not come back', () => {
    client.read().subscribe({ error: () => undefined });
    http
      .expectOne('/api/v1/users/me/preferences')
      .flush({ success: false }, { status: 503, statusText: 'Service Unavailable' });
    expect(settings.prefsState()).toBe('failed');
    expect(settings.serverPrefs()).toBeNull();
  });

  it('sends only the keys it is given, so the other app’s choices stand', () => {
    client.update({ businessTier: true }).subscribe();
    const req = http.expectOne('/api/v1/users/me/preferences');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ businessTier: true });
    req.flush(ok({ ...SERVER, businessTier: true }));
  });

  it('writes the answer into the classic session cache', () => {
    client.update({ visionModelPreference: 'PLANTNET' }).subscribe();
    http
      .expectOne('/api/v1/users/me/preferences')
      .flush(ok({ ...SERVER, visionModelPreference: 'PLANTNET' }));
    const cached = JSON.parse(sessionStorage.getItem(PREFS_CACHE_KEY) ?? '{}');
    expect(cached.visionModelPreference).toBe('PLANTNET');
    expect(settings.serverPrefs()!.visionModelPreference).toBe('PLANTNET');
  });
});
