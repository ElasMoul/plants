import { Injectable } from '@angular/core';
import { Language } from './language.service';

export interface SectionVariant { language: Language; status: string; texts: Record<string,string>; }
export interface SectionView { id: string; originalLanguage: Language; targetLanguage: Language; variants: SectionVariant[]; }
@Injectable()
export class SectionLanguageState {
  view?: SectionView;
  enabled = false;
  language: Language = 'en';
  select(language: Language): void {
    this.language = language;
    try { if (this.view) localStorage.setItem(`plantpal.section.${this.view.id}`, language); } catch { /* Keep the selection for this view. */ }
  }
  accept(view: SectionView): void {
    this.enabled = true;
    this.view = view;
    let saved: Language | null = null;
    try { saved = localStorage.getItem(`plantpal.section.${view.id}`) as Language | null; } catch { /* Use the original language. */ }
    const targetReady = view.variants.some(v => v.language === view.targetLanguage && v.status === 'READY');
    const desired = targetReady ? view.targetLanguage : saved ?? view.originalLanguage;
    this.language = view.variants.some(v => v.language === desired && v.status === 'READY') ? desired : view.originalLanguage;
  }
  get awaitingOriginal(): boolean {
    return !!this.view && this.language === this.view.originalLanguage && this.language !== 'en'
      && !this.view.variants.some(v => v.language === this.language && v.status === 'READY');
  }
  text(source: string | null | undefined): string {
    if (!source || (this.enabled && !this.view) || this.awaitingOriginal) return '';
    return this.view?.variants.find(v => v.language === this.language && v.status === 'READY')?.texts[source] ?? source;
  }
}
