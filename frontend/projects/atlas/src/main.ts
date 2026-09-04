import { bootstrapApplication } from '@angular/platform-browser';
import { consumeSessionHandoff } from '@plantpal/shared-core';
import { appConfigFor } from './app/app.config';
import { resolveMockMode } from './app/core/mock-mode';
import { applyBootAppearance } from './app/settings/settings.store';
import { environment } from './environments/environment';
import { App } from './app/app';

// A login on the classic app can arrive here carrying its session in the URL
// fragment ("Continue into the Atlas"). Consume it BEFORE bootstrap so the very
// first AuthService read already sees the signed-in session, and the fragment is
// scrubbed before anything renders.
consumeSessionHandoff();

// The remembered interface and palette are painted on <html> before the first
// node renders — index.html's defaults would otherwise flash first.
applyBootAppearance();

// Resolved HERE, in main's body — a module-level constant would run before the
// handoff above.
const mode = resolveMockMode(window, environment);

bootstrapApplication(App, appConfigFor(mode))
  .catch((err) => console.error(err));
