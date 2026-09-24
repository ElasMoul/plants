import { ChangeDetectorRef, Directive, ElementRef, Input, OnChanges, OnDestroy, Renderer2 } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, timer, of } from 'rxjs';
import { switchMap, filter, take, timeout } from 'rxjs/operators';
import { translate } from './language.service';
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
    const view = this.state.view;
    if (!view || this.state.language === view.targetLanguage) return;
    const saved = view.variants.find(v => v.language === view.targetLanguage);
    if (saved?.status === 'READY') {
      this.state.select(view.targetLanguage); this.error = ''; this.draw(); return;
    }
    this.busy = true; this.error = ''; this.draw();
    const request = saved?.status === 'PENDING' ? of({ data: view })
      : this.http.post<{data:SectionView}>(this.url + '/translate', {});
    this.active.add(request.pipe(
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
    if (this.toolbar) this.renderer.removeChild(this.toolbar.parentNode, this.toolbar);
    this.toolbar = undefined;
  }
  private draw(): void {
    this.cdr.markForCheck();
    this.clear();
    const host = this.element.nativeElement;
    this.renderer.setAttribute(host, 'dir', this.state.language === 'ar' ? 'rtl' : 'ltr');
    const view = this.state.view;
    if (!view || (this.state.language === view.targetLanguage && !this.error)) return;
    const bar = this.renderer.createElement('span') as HTMLElement; this.toolbar = bar;
    this.renderer.addClass(bar, 'section-language-controls');
    this.renderer.setAttribute(bar, 'dir', document.documentElement.dir);
    const audio = host.querySelector('app-read-aloud-button');
    if (audio?.parentNode) this.renderer.insertBefore(audio.parentNode, bar, audio.nextSibling);
    else this.renderer.insertBefore(host, bar, host.firstChild);
    if (this.state.language !== view.targetLanguage) {
      this.button(bar, translate('Translate to {0}', [view.targetLanguage.toUpperCase()]), () => this.request(), this.busy);
    }
    if (this.busy || this.error) {
      const status = this.renderer.createElement('span'); status.textContent = this.error || translate('Translating…');
      this.renderer.setAttribute(status, 'role', 'status'); this.renderer.appendChild(bar, status);
    }
    this.renderer.setAttribute(this.element.nativeElement, 'dir', this.state.language === 'ar' ? 'rtl' : 'ltr');
  }
  private button(bar: HTMLElement, text: string, action: () => void, disabled: boolean): void {
    const button = this.renderer.createElement('button') as HTMLButtonElement;
    button.type = 'button'; button.disabled = disabled; button.title = text;
    this.renderer.setAttribute(button, 'aria-label', text);
    this.renderer.addClass(button, 'section-translate-button');
    const icon = this.renderer.createElement('span');
    this.renderer.addClass(icon, 'material-icons');
    this.renderer.setAttribute(icon, 'aria-hidden', 'true');
    icon.textContent = 'translate'; this.renderer.appendChild(button, icon);
    this.renderer.appendChild(bar, button); this.listeners.push(this.renderer.listen(button, 'click', (event: Event) => { event.stopPropagation(); action(); }));
  }
}
