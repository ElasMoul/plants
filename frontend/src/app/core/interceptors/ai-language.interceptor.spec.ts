import { fakeAsync, tick } from '@angular/core/testing';
import { HttpHandler, HttpRequest, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { AiLanguageInterceptor } from './ai-language.interceptor';
import { AiTranslationState } from '../../shared/i18n/ai-translation-state.service';
import { aiDiagram, aiText, rememberAiTexts } from '../../shared/i18n/ai-text.pipe';

describe('AI content localization', () => {
  afterEach(() => localStorage.removeItem('plantpal.language'));

  it('polls only translation jobs after POST and leaves action data unchanged', fakeAsync(() => {
    localStorage.setItem('plantpal.language', 'fr');
    const source = { instruction: 'Use 5 ml.', frequencyDays: 7, completedAt: '2026-09-23' };
    const response = new HttpResponse({ body: { data: source, localization: { id: 'job', status: 'PENDING' } } });
    const handle = jest.fn().mockReturnValueOnce(of(response)).mockReturnValueOnce(of(new HttpResponse({ body: {
      data: { id: 'job', status: 'READY', texts: { 'Use 5 ml.': 'Utilisez 5 ml.' } },
    } })));
    const state = new AiTranslationState();
    let received: unknown;
    new AiLanguageInterceptor(state).intercept(new HttpRequest('POST', '/api/v1/treatments/1/craft-plan', {}),
      { handle } as HttpHandler).subscribe(event => received = event);
    expect(state.pending()).toBe(1);
    tick(0);
    expect(handle.mock.calls.map(call => call[0].method)).toEqual(['POST', 'GET']);
    expect(handle.mock.calls[1][0].url).toBe('/api/v1/translations/job');
    expect(received).toBe(response);
    expect(source.instruction).toBe('Use 5 ml.');
    expect(aiText(source.instruction)).toBe('Utilisez 5 ml.');
    expect(state.pending()).toBe(0);
  }));

  it('shows a failed status while retaining original content', () => {
    localStorage.setItem('plantpal.language', 'fr');
    const state = new AiTranslationState();
    const response = new HttpResponse({ body: { data: { description: 'Original.' }, localization: { id: 'failed', status: 'FAILED' } } });
    new AiLanguageInterceptor(state).intercept(new HttpRequest('GET', '/api/v1/species/1'),
      { handle: () => of(response) }).subscribe();
    expect(state.failed()).toEqual(['failed']);
    expect(aiText('Original.')).toBe('Original.');
  });

  it('keeps English requests and display unchanged', () => {
    localStorage.setItem('plantpal.language', 'en');
    const request = new HttpRequest('GET', '/api/v1/species/1');
    const handle = jest.fn().mockReturnValue(of(new HttpResponse()));
    new AiLanguageInterceptor(new AiTranslationState()).intercept(request, { handle }).subscribe();
    expect(handle).toHaveBeenCalledWith(request);
    expect(aiText('Use 5 ml.')).toBe('Use 5 ml.');
  });

  it('translates diagram labels without changing node identifiers or connections', () => {
    localStorage.setItem('plantpal.language', 'fr');
    rememberAiTexts({ 'Water plant': 'Arroser la plante', 'Wait 7 days': 'Attendre 7 jours' });
    expect(aiDiagram('flowchart TD\n A[Water plant] --> B[Wait 7 days]'))
      .toBe('flowchart TD\n A["Arroser la plante"] --> B["Attendre 7 jours"]');
  });
});
