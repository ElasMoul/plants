import { TestBed } from '@angular/core/testing';
import { Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { AuthService } from '@plantpal/shared-core';
import { AuthCatchAllGuard } from './auth-catch-all.guard';

describe('AuthCatchAllGuard', () => {
  let loggedIn: boolean;
  let guard: AuthCatchAllGuard;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isLoggedIn: () => loggedIn } }],
    });
    guard = TestBed.inject(AuthCatchAllGuard);
    router = TestBed.inject(Router);
  });

  const visit = (url: string) =>
    router.serializeUrl(guard.canActivate(null, { url } as RouterStateSnapshot) as never);

  it('sends a signed-in user on an unknown link to the garden', () => {
    loggedIn = true;
    expect(visit('/nope')).toBe('/garden');
  });

  it('sends a signed-out visitor straight to login, preserving the deep link', () => {
    loggedIn = false;
    expect(visit('/plants/42?tab=care')).toBe('/login?returnUrl=%2Fplants%2F42%3Ftab%3Dcare');
  });
});
