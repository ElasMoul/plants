import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of, timer } from 'rxjs';
import { catchError, filter, finalize, map, switchMap, take, timeout } from 'rxjs/operators';
import { readLanguage } from '../../shared/i18n/language.service';
import { rememberAiTexts } from '../../shared/i18n/ai-text.pipe';
import { AiTranslationState } from '../../shared/i18n/ai-translation-state.service';

interface Translation {
  id: string;
  status: 'PENDING' | 'READY' | 'FAILED';
  texts: Record<string, string>;
}

@Injectable()
export class AiLanguageInterceptor implements HttpInterceptor {
  constructor(private readonly state: AiTranslationState) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (readLanguage() === 'en' || request.responseType !== 'json'
        || !/\/api\/v1\/(identifications|species|treatments|treatment-plans|reminders|care|plants)(?:\/|\?|$)/.test(request.url)) {
      return next.handle(request);
    }
    const localized = request.clone({ setHeaders: { 'X-Content-Language': readLanguage() } });
    return next.handle(localized).pipe(switchMap(event => {
      if (!(event instanceof HttpResponse)) return of(event);
      const job = (event.body as { localization?: Translation } | null)?.localization;
      if (!job) return of(event);
      if (job.status !== 'PENDING') { this.accept(job); return of(event); }
      return this.wait(job, localized, next).pipe(map(() => event));
    }));
  }

  private wait(job: Translation, request: HttpRequest<unknown>, next: HttpHandler): Observable<void> {
    this.state.start();
    const base = request.url.slice(0, request.url.indexOf('/api/v1/'));
    const poll = new HttpRequest('GET', `${base}/api/v1/translations/${job.id}`, { headers: request.headers });
    return timer(0, 1500).pipe(
      switchMap(() => next.handle(poll)),
      filter((event): event is HttpResponse<{ data: Translation }> => event instanceof HttpResponse),
      map(event => event.body!.data),
      filter(result => result.status !== 'PENDING'),
      take(1),
      timeout(120000),
      map(result => this.accept(result)),
      catchError(() => { this.state.fail(job.id); return of(undefined); }),
      finalize(() => this.state.finish()),
    );
  }

  private accept(job: Translation): void {
    if (job.status === 'READY') rememberAiTexts(job.texts);
    else this.state.fail(job.id);
  }
}
