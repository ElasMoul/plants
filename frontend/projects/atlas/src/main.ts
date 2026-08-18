import { bootstrapApplication } from '@angular/platform-browser';
import { consumeSessionHandoff } from '@plantpal/shared-core';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// A login on the classic app can arrive here carrying its session in the URL
// fragment ("Continue into the Atlas"). Consume it BEFORE bootstrap so the very
// first AuthService read already sees the signed-in session, and the fragment is
// scrubbed before anything renders.
consumeSessionHandoff();

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
