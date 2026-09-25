import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../tokens';
import { SESSION_TOKEN_KEY, SESSION_USER_KEY } from '../session-handoff';
import { AuthService } from './auth.service';

/** An unsigned JWT with the given payload, base64url-encoded like a real one. */
function jwt(payload: Record<string, unknown>): string {
  const b64url = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64url({ alg: 'HS256' })}.${b64url(payload)}.signature`;
}

const inOneHour = () => Math.floor(Date.now() / 1000) + 3600;
const anHourAgo = () => Math.floor(Date.now() / 1000) - 3600;

describe('AuthService (shared-core)', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('login stores the token, user and preferred language', () => {
    const token = jwt({ exp: inOneHour() });
    service.login({ email: 'a@b.c', password: 'pw' }).subscribe();

    http
      .expectOne('/api/v1/auth/login')
      .flush({ data: { token, language: 'fr', user: { id: 1, email: 'a@b.c' } } });

    expect(localStorage.getItem(SESSION_TOKEN_KEY)).toBe(token);
    expect(localStorage.getItem('plantpal.language')).toBe('fr');
    expect(service.getCurrentUser()).toEqual({ id: 1, email: 'a@b.c' });
    expect(service.isLoggedIn()).toBe(true);
    expect(service.getToken()).toBe(token);
  });

  it('register posts to /auth/register and signs the user in', () => {
    const token = jwt({ exp: inOneHour() });
    service
      .register({ email: 'a@b.c', password: 'pw', firstName: 'A', lastName: 'B' })
      .subscribe();

    const req = http.expectOne('/api/v1/auth/register');
    expect(req.request.method).toBe('POST');
    req.flush({ data: { token, user: { id: 2 } } });

    expect(service.isLoggedIn()).toBe(true);
    expect(localStorage.getItem('plantpal.language')).toBeNull();
  });

  it('an expired token is not a session, and getToken clears it', () => {
    localStorage.setItem(SESSION_TOKEN_KEY, jwt({ exp: anHourAgo() }));
    localStorage.setItem(SESSION_USER_KEY, JSON.stringify({ id: 1 }));

    expect(service.isLoggedIn()).toBe(false);
    expect(service.getToken()).toBeNull();
    expect(localStorage.getItem(SESSION_TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(SESSION_USER_KEY)).toBeNull();
  });

  it('a malformed or missing token is treated as signed out', () => {
    expect(service.isLoggedIn()).toBe(false);
    localStorage.setItem(SESSION_TOKEN_KEY, 'not-a-jwt');
    expect(service.isLoggedIn()).toBe(false);
    localStorage.setItem(SESSION_TOKEN_KEY, 'a.%%%.c');
    expect(service.getToken()).toBeNull();
  });

  it('decodes base64url payloads that contain - and _ characters', () => {
    // "~~~" and "???" encode to base64 with "+" and "/" — base64url turns them into "-" and "_".
    const token = jwt({ exp: inOneHour(), pad: '~~~???>>>' });
    expect(token.split('.')[1]).toMatch(/[-_]/);
    localStorage.setItem(SESSION_TOKEN_KEY, token);

    expect(service.isLoggedIn()).toBe(true);
  });

  it('logout clears the session; a corrupt stored user reads as none', () => {
    localStorage.setItem(SESSION_TOKEN_KEY, jwt({ exp: inOneHour() }));
    localStorage.setItem(SESSION_USER_KEY, '{broken');

    expect(service.getCurrentUser()).toBeNull();
    service.logout();
    expect(service.isLoggedIn()).toBe(false);
    expect(service.getCurrentUser()).toBeNull();
  });
});
