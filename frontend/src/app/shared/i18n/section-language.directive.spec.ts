import { Component } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SectionLanguageDirective } from './section-language.directive';
import { AiTextPipe } from './ai-text.pipe';
import { SectionView } from './section-language.state';

@Component({ standalone: true, imports: [SectionLanguageDirective, AiTextPipe],
  template: `<section appSection="scan:1:health"><p>{{ 'Water roots.' | aiText }}</p></section>` })
class Host {}

describe('Explicit section translations', () => {
  const url = '/api/v1/content-sections/scan/1/health';
  const original: SectionView = { id: 'section-1', originalLanguage: 'en', targetLanguage: 'fr',
    variants: [{ language: 'en', status: 'READY', texts: { 'Water roots.': 'Water roots.' } }] };
  let http: HttpTestingController;
  beforeEach(() => {
    localStorage.clear(); localStorage.setItem('plantpal.language', 'fr');
    TestBed.configureTestingModule({ imports: [Host], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); localStorage.clear(); });
  it('keeps original text until an explicit click and reuses saved versions without requests', () => {
    const fixture = TestBed.createComponent(Host); fixture.detectChanges();
    http.expectOne(url).flush({ data: original }); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').textContent).toBe('Water roots.');
    fixture.nativeElement.querySelector('button').click();
    const request = http.expectOne(url + '/translate'); expect(request.request.method).toBe('POST');
    request.flush({ data: { ...original, variants: [...original.variants,
      { language: 'fr', status: 'READY', texts: { 'Water roots.': 'Arrosez les racines.' } }] } });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').textContent).toBe('Arrosez les racines.');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    const select = fixture.nativeElement.querySelector('select');
    select.value = 'en'; select.dispatchEvent(new Event('change')); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').textContent).toBe('Water roots.');
    expect(localStorage.getItem('plantpal.section.section-1')).toBe('en');
    fixture.destroy();
  });
  it('uses the generation language even when the app language differs', () => {
    const fixture = TestBed.createComponent(Host); fixture.detectChanges();
    http.expectOne(url).flush({ data: { ...original, originalLanguage: 'ar', variants: [...original.variants,
      { language: 'ar', status: 'READY', texts: { 'Water roots.': 'اسقِ الجذور.' } }] } });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').textContent).toBe('اسقِ الجذور.');
    expect(fixture.nativeElement.querySelector('section').dir).toBe('rtl');
    fixture.destroy();
  });
  it('stops waiting after two minutes without resubmitting a translation', fakeAsync(() => {
    const fixture = TestBed.createComponent(Host); fixture.detectChanges();
    http.expectOne(url).flush({ data: original }); fixture.detectChanges();
    fixture.nativeElement.querySelector('button').click();
    const pending = { ...original, variants: [...original.variants, { language: 'fr', status: 'PENDING', texts: {} }] };
    http.expectOne(url + '/translate').flush({ data: pending });
    for (let i = 0; i < 60; i++) { tick(i === 0 ? 1000 : 2000); http.expectOne(url).flush({ data: pending }); }
    tick(1001); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=status]').textContent).toContain('Échec');
    http.expectNone(url + '/translate'); fixture.destroy();
  }));
});
