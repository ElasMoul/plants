import { inject, Injectable } from '@angular/core';
import { ApiResponse, User } from '@plantpal/shared-core';
import { Observable, of } from 'rxjs';
import { MockBackend } from './mock-backend';

/** A far-future exp so the shared JWT-expiry checks are happy without a server. */
const MOCK_TOKEN = 'mock.eyJleHAiOjQ3MDAwMDAwMDB9.mock';

/**
 * The mock garden's session. It answers "signed in" so the world loads, but it
 * NEVER writes plantpal_token / plantpal_user — the classic app on a shared
 * origin must not be fooled into thinking someone is signed in.
 */
@Injectable()
export class MockAuthService {
  private readonly backend = inject(MockBackend);

  isLoggedIn(): boolean {
    return true;
  }

  getToken(): string {
    return MOCK_TOKEN;
  }

  getCurrentUser(): User {
    return this.backend.state.user;
  }

  logout(): void {
    /* there is no session to end in the mock garden */
  }

  login(): Observable<ApiResponse<{ token: string; user: User }>> {
    return of({
      success: true,
      message: 'Signed in to the mock garden',
      timestamp: new Date().toISOString(),
      data: { token: this.getToken(), user: this.getCurrentUser() },
    });
  }

  register(): Observable<ApiResponse<{ token: string; user: User }>> {
    return this.login();
  }
}
