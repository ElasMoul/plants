import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { AuthService, provideSharedCore } from '@plantpal/shared-core';
import { environment } from '../environments/environment';
import { atlasAuthInterceptor } from './core/atlas-auth.interceptor';
import { MOCK_MODE, MockMode, resolveMockMode } from './core/mock-mode';
import { mockApiInterceptor } from './mock/mock-api.interceptor';
import { MockAuthService } from './mock/mock-auth.service';

/**
 * The app's providers for a given data source. The mock interceptor sits FIRST
 * so it answers before the auth interceptor bothers to attach a token; in live
 * mode it is a pass-through and this config is byte-for-byte the live one.
 */
export function appConfigFor(mode: MockMode): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideZoneChangeDetection({ eventCoalescing: true }),
      { provide: MOCK_MODE, useValue: mode },
      // The atlas auth interceptor attaches the shared session's bearer token.
      provideHttpClient(withInterceptors([mockApiInterceptor, atlasAuthInterceptor])),
      // Shared session with the classic app (same AuthService + token keys).
      ...provideSharedCore({ apiBaseUrl: environment.apiUrl }),
      // The mock garden has no login — and never writes the shared session keys.
      ...(mode.enabled ? [{ provide: AuthService, useClass: MockAuthService }] : []),
    ],
  };
}

export const appConfig: ApplicationConfig = appConfigFor(resolveMockMode(window, environment));
