import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '@plantpal/shared-core';
import { atlasAuthInterceptor } from './atlas-auth.interceptor';
import { provideMockModeOff } from './mock-mode';

describe('atlasAuthInterceptor (E1 — shared session)', () => {
  let http: HttpClient;
  let mock: HttpTestingController;
  let token: string | null;

  beforeEach(() => {
    token = null;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([atlasAuthInterceptor])),
        provideHttpClientTesting(),
        provideMockModeOff(),
        { provide: AuthService, useValue: { getToken: () => token } },
      ],
    });
    http = TestBed.inject(HttpClient);
    mock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => mock.verify());

  it('attaches the shared bearer token when signed in', () => {
    token = 'SHARED_JWT';
    http.get('/api/v1/dashboard').subscribe();
    const req = mock.expectOne('/api/v1/dashboard');
    expect(req.request.headers.get('Authorization')).toBe('Bearer SHARED_JWT');
    req.flush({});
  });

  it('omits Authorization when there is no session', () => {
    http.get('/api/v1/dashboard').subscribe();
    const req = mock.expectOne('/api/v1/dashboard');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('always sends a correlation id', () => {
    http.get('/api/v1/dashboard').subscribe();
    const req = mock.expectOne('/api/v1/dashboard');
    expect(req.request.headers.get('X-Correlation-ID')).toBeTruthy();
    req.flush({});
  });
});
