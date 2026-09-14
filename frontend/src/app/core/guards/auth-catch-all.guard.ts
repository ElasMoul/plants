import { Injectable } from '@angular/core';
import { CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from '@plantpal/shared-core';

// ADR-5: replaces the auth module's static `{ path: '**', redirectTo: '/garden' }`.
// A static redirectTo can't see auth state, so a signed-out visitor on an unknown
// deep link took two hops to reach /login: '**' -> /garden -> AuthGuard -> /login.
// This guard makes the same decision AuthGuard would make, in one hop, for the
// one route it fronts.
@Injectable({ providedIn: 'root' })
export class AuthCatchAllGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  canActivate(_route: unknown, state: RouterStateSnapshot): boolean | UrlTree {
    if (this.authService.isLoggedIn()) {
      return this.router.createUrlTree(['/garden']);
    }
    return this.router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
}
