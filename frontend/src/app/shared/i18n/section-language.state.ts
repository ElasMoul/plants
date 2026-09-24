import { Injectable } from '@angular/core';
import { Language } from './language.service';

export interface SectionVariant { language: Language; status: string; texts: Record<string,string>; }
export interface SectionView { id: string; originalLanguage: Language; targetLanguage: Language; variants: SectionVariant[]; }
@Injectable()
export class SectionLanguageState {
  view?: SectionView;
  language: Language = 'en';
  select(language: Language): void {
    this.language = language;
    try { if (this.view) localStorage.setItem(`plantpal.section.${this.view.id}`, language); } catch { /* Keep the selection for this view. */ }
  }
  accept(view: SectionView): void {
    this.view = view;
    let saved: Language | null = null;
    try { saved = localStorage.getItem(`plantpal.section.${view.id}`) as Language | null; } catch { /* Use the original language. */ }
    const desired = saved ?? view.originalLanguage;
    this.language = view.variants.some(v => v.language === desired && v.status === 'READY') ? desired : 'en';
  }
  text(source: string | null | undefined): string {
    if (!source) return '';
    return this.view?.variants.find(v => v.language === this.language && v.status === 'READY')?.texts[source] ?? source;
  }
}
