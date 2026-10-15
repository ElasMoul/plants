import { APP_INITIALIZER, NgModule } from '@angular/core';
import { INITIAL_CONFIG, ServerModule } from '@angular/platform-server';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { AppComponent } from './app.component';
import { AppModule } from './app.module';
import { serverRoutes } from './app.routes.server';
import { setServerLanguage } from './shared/i18n/language.service';
import { languageFromPath } from './shared/i18n/public-routes';

/** Pins the language from the prerendered URL before the first component renders. */
export function pinServerLanguage(config: { url?: string }): () => void {
  return () => setServerLanguage(languageFromPath(new URL(config.url ?? '/', 'http://localhost').pathname));
}

@NgModule({
  imports: [AppModule, ServerModule],
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    { provide: APP_INITIALIZER, useFactory: pinServerLanguage, deps: [INITIAL_CONFIG], multi: true },
  ],
  bootstrap: [AppComponent],
})
export class AppServerModule {}
