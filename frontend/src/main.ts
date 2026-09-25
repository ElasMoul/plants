import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import * as Sentry from '@sentry/angular';
import { AppModule } from './app/app.module';
import { environment } from './environments/environment';

Sentry.init({
  dsn: environment.sentryDsn,
  environment: environment.production ? 'production' : 'development',
  integrations: [Sentry.browserTracingIntegration()],
  tracesSampleRate: 0.1,
  enabled: !!environment.sentryDsn,
});

// Vercel Web Analytics + Speed Insights, dependency-free (@vercel/analytics' optional Svelte peer
// conflicts with Angular's vite). Same snippet Vercel documents for plain HTML:
// queue calls on window.va until the script served by Vercel loads.
if (environment.vercelAnalytics) {
  const w = window as unknown as { va?: (...args: unknown[]) => void; vaq?: unknown[][] };
  w.va = w.va || ((...args: unknown[]) => { (w.vaq = w.vaq || []).push(args); });
  const script = document.createElement('script');
  script.defer = true;
  script.src = '/_vercel/insights/script.js';
  document.head.appendChild(script);

  // Vercel Speed Insights (Core Web Vitals), same dependency-free pattern:
  // queue on window.si until /_vercel/speed-insights/script.js loads.
  const s = window as unknown as { si?: (...args: unknown[]) => void; siq?: unknown[][] };
  s.si = s.si || ((...args: unknown[]) => { (s.siq = s.siq || []).push(args); });
  const speedScript = document.createElement('script');
  speedScript.defer = true;
  speedScript.src = '/_vercel/speed-insights/script.js';
  document.head.appendChild(speedScript);
}

platformBrowserDynamic()
  .bootstrapModule(AppModule)
  .catch(err => console.error(err));
