import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { API_BASE_URL, SESSION_TOKEN_KEY } from '@plantpal/shared-core';

import { AuthGuard } from './auth.guard';

describe('AuthGuard defect characterization', () => {
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

  it('[PP-AUTH-001][authguard] returns a /login UrlTree for a signed-out /garden navigation', () => {
    const result = guard.canActivate();

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/mutation-login');
  });

  it('[PP-AUTH-001][authguard] admits a forged future-exp token as the pinned current boundary', () => {
    localStorage.setItem(SESSION_TOKEN_KEY, forgedFutureToken());

    expect(guard.canActivate()).toBe(true);
  });
});

function forgedFutureToken(): string {
  const header = encodeSegment({ alg: 'none', typ: 'JWT' });
  const payload = encodeSegment({ exp: Math.floor(Date.now() / 1000) + 3600 });
  return `${header}.${payload}.forged-signature`;
}

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
