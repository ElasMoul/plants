import { InjectionToken, Provider } from '@angular/core';

/**
 * Base URL for the PlantPal backend API (e.g. '/api/v1'). Provided by each host
 * application from its own environment, so shared-core stays free of any single
 * app's environment file.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');

export interface SharedCoreConfig {
  /** API base URL, e.g. environment.apiUrl ('/api/v1'). */
  apiBaseUrl: string;
}

/**
 * Wires shared-core into an application's DI. Call from the classic app's module
 * providers or the atlas app's ApplicationConfig:
 *   providers: [...provideSharedCore({ apiBaseUrl: environment.apiUrl })]
 */
export function provideSharedCore(config: SharedCoreConfig): Provider[] {
  return [{ provide: API_BASE_URL, useValue: config.apiBaseUrl }];
}
