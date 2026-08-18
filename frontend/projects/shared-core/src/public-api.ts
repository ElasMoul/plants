/*
 * Public API Surface of shared-core.
 *
 * Session/HTTP essentials shared by every PlantPal frontend (classic + atlas).
 * Deliberately free of Angular Router and Angular Material so no host app inherits
 * UI dependencies. Domain API clients (plant/species/treatment/reminder) are added
 * here in Phase D, when the atlas app consumes them.
 */
export { API_BASE_URL, provideSharedCore } from './lib/tokens';
export type { SharedCoreConfig } from './lib/tokens';

export * from './lib/models/api-response.model';
export * from './lib/models/user.model';

export { AuthService } from './lib/services/auth.service';
export { UserService } from './lib/services/user.service';
