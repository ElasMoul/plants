import { ChangeDetectorRef, Directive, ElementRef, Input, OnChanges, OnDestroy, Renderer2 } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, timer, of } from 'rxjs';
import { switchMap, filter, take, timeout } from 'rxjs/operators';
import { Language, translate } from './language.service';
import { SectionLanguageState, SectionView } from './section-language.state';

/** Section-local display state: changing UI language never switches this section's version. */
@Directive({ selector: '[appSection]', standalone: true, providers: [SectionLanguageState] })
export class SectionLanguageDirective implements OnChanges, OnDestroy {
  @Input() appSection: string | null = null;
  private key = '';
  private toolbar?: HTMLElement;
  private active = new Subscription();
  private listeners: (() => void)[] = [];
  private busy = false;
  private error = '';
  constructor(private readonly http: HttpClient, private readonly element: ElementRef<HTMLElement>,
    private readonly renderer: Renderer2, public readonly state: SectionLanguageState, private readonly cdr: ChangeDetectorRef) {}

  ngOnChanges(): void {
    if (!this.appSection || this.appSection === this.key || this.appSection.includes('null')) return;
    this.key = this.appSection; this.active.unsubscribe(); this.active = new Subscription();
    this.busy = false; this.error = ''; this.state.view = undefined;
    this.load();
  }
  ngOnDestroy(): void { this.active.unsubscribe(); this.clear(); }
  private get url(): string { return '/api/v1/content-sections/' + this.key.split(':').map(encodeURIComponent).join('/'); }
  private load(): void {
    this.error = '';
    this.active.add(this.http.get<{data:SectionView}>(this.url).pipe(
      switchMap(initial => {
        this.state.accept(initial.data); this.draw();
        const original = initial.data.originalLanguage;
        if (!initial.data.variants.some(v => v.language === original && v.status === 'PENDING')) return of(initial);
        // Only observe an existing generation job. Page reads never start or retry AI work.
        return timer(1000, 2000).pipe(switchMap(() => this.http.get<{data:SectionView}>(this.url)),
          filter(res => !res.data.variants.some(v => v.language === original && v.status === 'PENDING')),
          take(1), timeout(120000));
      }),
    ).subscribe({
      next: res => { this.state.accept(res.data); this.draw(); },
      error: () => { this.error = translate('Could not load section languages.'); this.draw(); },
    }));
  }
  private request(): void {
    if (this.busy) return;
    this.busy = true; this.error = ''; this.draw();
    this.active.add(this.http.post<{data:SectionView}>(this.url + '/translate', {}).pipe(
      switchMap(initial => initial.data.variants.some(v => v.language === initial.data.targetLanguage && v.status === 'PENDING')
        ? timer(1000, 2000).pipe(switchMap(() => this.http.get<{data:SectionView}>(this.url)),
          filter(res => !res.data.variants.some(v => v.language === initial.data.targetLanguage && v.status === 'PENDING')), take(1), timeout(120000))
        : [initial]),
    ).subscribe({
      next: res => {
        this.state.accept(res.data);
        const target = res.data.variants.find(v => v.language === res.data.targetLanguage);
        if (target?.status === 'READY') { this.busy = false; this.state.select(target.language); }
        else if (target?.status === 'FAILED') { this.busy = false; this.error = translate('Translation failed. Try again.'); }
        this.draw();
      }, error: () => { this.busy = false; this.error = translate('Translation failed. Try again.'); this.draw(); },
    }));
  }
  private clear(): void {
    this.listeners.forEach(stop => stop()); this.listeners = [];
    if (this.toolbar) this.renderer.removeChild(this.element.nativeElement, this.toolbar);
    this.toolbar = undefined;
  }
  private draw(): void {
    this.cdr.markForCheck();
    this.clear();
    const bar = this.renderer.createElement('div') as HTMLElement; this.toolbar = bar;
    this.renderer.addClass(bar, 'section-language-controls');
    this.renderer.setAttribute(bar, 'dir', document.documentElement.dir);
    this.renderer.insertBefore(this.element.nativeElement, bar, this.element.nativeElement.firstChild);
    const view = this.state.view;
    if (view) {
      const select = this.renderer.createElement('select') as HTMLSelectElement;
      this.renderer.setAttribute(select, 'aria-label', translate('Saved versions'));
      for (const variant of view.variants.filter(v => v.status === 'READY')) {
        const option = this.renderer.createElement('option'); option.value = variant.language;
        option.textContent = variant.language.toUpperCase(); this.renderer.appendChild(select, option);
      }
      select.value = this.state.language;
      this.renderer.appendChild(bar, select);
      this.listeners.push(this.renderer.listen(select, 'change', () => { this.state.select(select.value as Language); this.draw(); }));
      const target = view.variants.find(v => v.language === view.targetLanguage);
      if (!target || target.status === 'FAILED') this.button(bar, translate('Translate to {0}', [view.targetLanguage.toUpperCase()]), () => this.request(), this.busy);
      if (view.variants.some(v => v.status === 'PENDING') && !this.busy) this.button(bar, translate('Refresh'), () => this.load(), false);
    } else this.button(bar, translate('Retry'), () => this.load(), false);
    if (this.busy || this.error) {
      const status = this.renderer.createElement('span'); status.textContent = this.error || translate('Translating…');
      this.renderer.setAttribute(status, 'role', 'status'); this.renderer.appendChild(bar, status);
    }
    this.renderer.setAttribute(this.element.nativeElement, 'dir', this.state.language === 'ar' ? 'rtl' : 'ltr');
  }
  private button(bar: HTMLElement, text: string, action: () => void, disabled: boolean): void {
    const button = this.renderer.createElement('button') as HTMLButtonElement;
    button.type = 'button'; button.textContent = text; button.disabled = disabled;
    this.renderer.appendChild(bar, button); this.listeners.push(this.renderer.listen(button, 'click', (event: Event) => { event.stopPropagation(); action(); }));
  }
}
