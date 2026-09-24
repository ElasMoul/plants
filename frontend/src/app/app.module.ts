import { MatDatepickerIntl } from '@angular/material/datepicker';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { localizedDatepicker } from './shared/i18n/localized-datepicker';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { localizedPaginator } from './shared/i18n/localized-paginator';
import { registerLocaleData } from '@angular/common';
import fr from '@angular/common/locales/fr';
import ar from '@angular/common/locales/ar';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { LanguageService } from './shared/i18n/language.service';
import { LanguageSwitchComponent } from './shared/i18n/language-switch.component';
registerLocaleData(fr);
registerLocaleData(ar);
import { LOCALE_ID, APP_INITIALIZER, ErrorHandler, NgModule, isDevMode } from '@angular/core';
import { noop } from 'rxjs';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { Router } from '@angular/router';
import { ServiceWorkerModule } from '@angular/service-worker';
import * as Sentry from '@sentry/angular';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';

import { provideSharedCore } from '@plantpal/shared-core';

import { environment } from '../environments/environment';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { CoreModule } from './core/core.module';
import { SharedModule } from './shared/shared.module';
import { JwtInterceptor } from './core/interceptors/jwt.interceptor';

@NgModule({ declarations: [AppComponent],
    bootstrap: [AppComponent], imports: [LanguageSwitchComponent, BrowserModule,
        BrowserAnimationsModule,
        AppRoutingModule,
        CoreModule,
        SharedModule,
        MatToolbarModule,
        MatButtonModule,
        MatIconModule,
        MatMenuModule,
        MatDividerModule,
        ServiceWorkerModule.register('ngsw-worker.js', {
            enabled: !isDevMode(),
            registrationStrategy: 'registerWhenStable:30000',
        })], providers: [
        { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { subscriptSizing: 'dynamic' } },
        { provide: MatDatepickerIntl, useFactory: localizedDatepicker },
        { provide: MatPaginatorIntl, useFactory: localizedPaginator },
        { provide: LOCALE_ID, useFactory: (language: LanguageService) => language.locale, deps: [LanguageService] },
        { provide: MAT_DATE_LOCALE, useFactory: (language: LanguageService) => language.locale, deps: [LanguageService] },
        { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true },
        { provide: ErrorHandler, useValue: Sentry.createErrorHandler() },
        { provide: Sentry.TraceService, deps: [Router] },
        {
            provide: APP_INITIALIZER,
            useFactory: () => noop,
            deps: [Sentry.TraceService],
            multi: true,
        },
        provideHttpClient(withInterceptorsFromDi()),
        ...provideSharedCore({ apiBaseUrl: environment.apiUrl }),
    ] })
export class AppModule {}
