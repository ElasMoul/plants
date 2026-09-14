import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { API_BASE_URL, SESSION_TOKEN_KEY } from '@plantpal/shared-core';

import { AuthGuard } from './auth.guard';

describe('AuthGuard redirect behavior (wave 2 — PP-AUTH-001)', () => {
  let guard: AuthGuard;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: HttpClient, useValue: {} },
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    guard = TestBed.inject(AuthGuard);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('redirects a signed-out deep-link navigation to /login with the attempted URL as returnUrl', () => {
    const state = { url: '/plants/42' } as RouterStateSnapshot;

    const result = guard.canActivate({} as ActivatedRouteSnapshot, state);

    expect(result).toBeInstanceOf(UrlTree);
    const serialized = router.serializeUrl(result as UrlTree);
    expect(serialized).toBe('/login?returnUrl=%2Fplants%2F42');
  });

  it('redirects a signed-out root navigation to /login carrying "/" as returnUrl', () => {
    const state = { url: '/' } as RouterStateSnapshot;

    const result = guard.canActivate({} as ActivatedRouteSnapshot, state);

    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2F');
  });

  // ADR-4: the guard is a deliberately optimistic, client-side-only check — it
  // decodes the token's own exp and never calls the server. This is not the
  // wave-1 pin's "defect" reframed as a pass; it is the architecture's actual
  // division of labour: the guard owns fast, felt-continuity routing, and the
  // server's 401 (enforced by the interceptor, and from wave 3 onward by the
  // session registry) is the only real authorization verdict. A forged-but-
  // well-formed future-exp token therefore *is* admitted here by design — the
  // very next authenticated request either succeeds against a live session or
  // comes back 401 and the interceptor's single-flight sign-out takes over.
  it('admits a structurally well-formed future-exp token without a server round-trip (ADR-4)', () => {
    localStorage.setItem(SESSION_TOKEN_KEY, forgedFutureToken());

    const result = guard.canActivate({} as ActivatedRouteSnapshot, { url: '/plants' } as RouterStateSnapshot);

    expect(result).toBe(true);
  });

  it('does not admit a token whose exp has already passed', () => {
    localStorage.setItem(SESSION_TOKEN_KEY, forgedExpiredToken());

    const result = guard.canActivate({} as ActivatedRouteSnapshot, { url: '/plants' } as RouterStateSnapshot);

    expect(result).toBeInstanceOf(UrlTree);
  });
});

function forgedFutureToken(): string {
  return forgedToken(Math.floor(Date.now() / 1000) + 3600);
}

function forgedExpiredToken(): string {
  return forgedToken(Math.floor(Date.now() / 1000) - 3600);
}

function forgedToken(exp: number): string {
  const header = encodeSegment({ alg: 'none', typ: 'JWT' });
  const payload = encodeSegment({ exp });
  return `${header}.${payload}.forged-signature`;
}

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
