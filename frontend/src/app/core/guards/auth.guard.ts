import { Injectable } from '@angular/core';
import { CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from '@plantpal/shared-core';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  // ADR-4: this check is deliberately optimistic — it decodes the token's own exp
  // client-side and never calls the server. That is by design, not an oversight:
  // a blocking GET /auth/session here would add a round-trip to every navigation
  // and still couldn't prevent a stale-token render race, so the guard stays fast
  // and the interceptor's 401 handling is the real enforcement (the server's
  // verdict, not the guard's). What the guard owns is the redirect experience:
  // sending a signed-out visitor to /login with the attempted destination
  // preserved as a validated returnUrl.
  canActivate(_route: unknown, state: RouterStateSnapshot): boolean | UrlTree {
    if (this.authService.isLoggedIn()) {
      return true;
    }
    return this.router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
}
