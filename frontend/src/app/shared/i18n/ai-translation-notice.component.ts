import { readLanguage } from './language.service';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AiTranslationState } from './ai-translation-state.service';

@Component({
  selector: 'app-ai-translation-notice', standalone: true, imports: [CommonModule],
  template: `<aside *ngIf="state.pending() || state.failed().length" role="status" aria-live="polite">
    <span *ngIf="state.pending()">{{ arabic ? 'جارٍ إعداد نص الذكاء الاصطناعي بالعربية…' : 'Préparation du texte IA en français…' }}</span>
    <span *ngIf="state.failed().length">{{ arabic ? 'الترجمة غير متاحة. يُعرض النص الأصلي. تحقق من رصيد استخدام الذكاء الاصطناعي أو أعد المحاولة.' : 'La traduction est indisponible. Le texte original est affiché. Vérifiez votre quota d’IA ou réessayez.' }}</span>
    <button *ngIf="state.failed().length" [disabled]="retrying" (click)="retry()">{{ arabic ? 'إعادة محاولة الترجمة' : 'Réessayer la traduction' }}</button>
  </aside>`,
  styles: [`aside { padding:12px 20px; margin:8px 16px; border-radius:12px; background:#f4edda;
    color:#423719; display:flex; gap:12px; flex-wrap:wrap; align-items:center; font-size:14px; }
    button { font:inherit; cursor:pointer; border:1px solid currentColor; border-radius:12px;
      padding:6px 12px; background:transparent; color:inherit; }`],
})
export class AiTranslationNoticeComponent {
  readonly arabic = readLanguage() === 'ar';
  retrying = false;
  constructor(public readonly state: AiTranslationState, private readonly http: HttpClient) {}
  retry(): void {
    const ids = this.state.failed().filter(Boolean);
    if (!ids.length) { window.location.reload(); return; }
    this.retrying = true;
    forkJoin(ids.map(id => this.http.post(`/api/v1/translations/${id}/retry`, {})))
      .pipe(finalize(() => this.retrying = false))
      .subscribe({ next: () => window.location.reload(), error: () => undefined });
  }
}
