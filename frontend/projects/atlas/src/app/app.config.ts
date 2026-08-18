import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideSharedCore } from '@plantpal/shared-core';
import { environment } from '../environments/environment';
import { atlasAuthInterceptor } from './core/atlas-auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    // The atlas auth interceptor attaches the shared session's bearer token.
    provideHttpClient(withInterceptors([atlasAuthInterceptor])),
    // Shared session with the classic app (same AuthService + token keys).
    ...provideSharedCore({ apiBaseUrl: environment.apiUrl }),
  ],
};
