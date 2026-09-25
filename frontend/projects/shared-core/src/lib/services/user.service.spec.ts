import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../tokens';
import { UserService } from './user.service';

describe('UserService (shared-core)', () => {
  let service: UserService;
  let http: HttpTestingController;
  const cached = () => JSON.parse(sessionStorage.getItem('ai_model_preferences') ?? 'null');

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads preferences and caches them for the session', () => {
    service.getPreferences().subscribe();
    http.expectOne('/api/v1/users/me/preferences').flush({ data: { visionModelPreference: 'PLANTNET' } });
    expect(cached()).toEqual({ visionModelPreference: 'PLANTNET' });
  });

  it('each update sends only its own fields (the backend leaves absent ones untouched)', () => {
    service.updateModelPreferences('ANTHROPIC_CLAUDE' as never, 'DEEPSEEK_R1' as never).subscribe();
    const models = http.expectOne('/api/v1/users/me/preferences');
    expect(models.request.method).toBe('PUT');
    expect(models.request.body).toEqual({
      visionModelPreference: 'ANTHROPIC_CLAUDE',
      reasoningModelPreference: 'DEEPSEEK_R1',
    });
    models.flush({ data: { v: 1 } });
    expect(cached()).toEqual({ v: 1 });

    service.updatePlantNetPreferences('weurope', 'fr').subscribe();
    const plantnet = http.expectOne('/api/v1/users/me/preferences');
    expect(plantnet.request.body).toEqual({ plantnetProject: 'weurope', plantnetLang: 'fr' });
    plantnet.flush({ data: { v: 2 } });

    service.updateBusinessTierPreference(true).subscribe();
    const tier = http.expectOne('/api/v1/users/me/preferences');
    expect(tier.request.body).toEqual({ businessTier: true });
    tier.flush({ data: { v: 3 } });
    expect(cached()).toEqual({ v: 3 });
  });
});
