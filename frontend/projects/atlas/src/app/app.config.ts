import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideSharedCore } from '@plantpal/shared-core';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptorsFromDi()),
    // Shared session with the classic app (same AuthService + token keys). apiBaseUrl
    // is the proxied backend path; a real atlas environment file lands in Phase E.
    ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
  ],
};
