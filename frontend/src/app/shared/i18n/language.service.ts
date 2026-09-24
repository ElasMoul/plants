import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { FR } from './fr';
import { AR } from './ar';
import { HttpClient } from '@angular/common/http';
import { Optional } from '@angular/core';
import { finalize } from 'rxjs/operators';

export type Language = 'en' | 'fr' | 'ar';
const STORAGE_KEY = 'plantpal.language';
const EN_LABELS: Readonly<Record<string, string>> = {
  WATERING: 'Watering', LIGHT: 'Light', HUMIDITY: 'Humidity', TEMPERATURE: 'Temperature',
  FERTILIZING: 'Fertilizing', REPOTTING: 'Repotting', PRUNING: 'Pruning', PEST: 'Pest',
  SEASONAL: 'Seasonal', BEGINNER_TIP: 'Beginner tip',
};
export const LANGUAGES: ReadonlyArray<{ code: Language; label: string; locale: string; direction: 'ltr' | 'rtl' }> = [
  { code: 'en', label: 'English', locale: 'en-US', direction: 'ltr' },
  { code: 'ar', label: 'العربية', locale: 'ar-MA', direction: 'rtl' },
  { code: 'fr', label: 'Français', locale: 'fr-FR', direction: 'ltr' },
];

export function readLanguage(): Language {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value === 'en' || value === 'fr' || value === 'ar') return value;
  } catch { /* Browser preference still works when storage is unavailable. */ }
  for (const locale of navigator.languages?.length ? navigator.languages : [navigator.language]) {
    const code = locale.toLowerCase().split('-')[0];
    if (code === 'en' || code === 'fr' || code === 'ar') return code;
  }
  return 'en';
}

export function pluralSuffix(count: number): string {
  if (readLanguage() === 'ar') return '';
  return new Intl.PluralRules(readLanguage()).select(count) === 'one' ? '' : 's';
}

/** Only call for application-owned copy, never for user or AI content. */
export function translate(message: string, values: readonly unknown[] = []): string {
  const summary = /^(\d+) issue\(s\)$/.exec(message);
  if (summary && readLanguage() === 'fr') return `${summary[1]} problème${pluralSuffix(Number(summary[1]))}`;
  if (readLanguage() === 'ar') {
    if (summary) return arabicCount(Number(summary[1]), 'issue');
    const counted = arabicCountMessage(message, Number(values[0]));
    if (counted !== undefined) return counted;
  }
  const catalog = readLanguage() === 'ar' ? AR : readLanguage() === 'fr' ? FR : EN_LABELS;
  const text = catalog[message] ?? message;
  return text.replace(/\{(\d+)\}/g, (match, index: string) =>
    Number(index) < values.length ? String(values[Number(index)] ?? '') : match);
}

@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly languages = LANGUAGES;
  readonly language = readLanguage();
  readonly locale = LANGUAGES.find(item => item.code === this.language)!.locale;

  saving = false;
  error = '';

  constructor(@Inject(DOCUMENT) document: Document, @Optional() private readonly http?: HttpClient) {
    document.documentElement.lang = this.language;
    document.documentElement.dir = LANGUAGES.find(item => item.code === this.language)!.direction;
  }

  select(language: string): void {
    if (this.saving || !LANGUAGES.some(item => item.code === language) || language === this.language) return;
    this.error = '';
    if (localStorage.getItem('plantpal_token') && this.http) {
      this.saving = true;
      this.http.put('/api/v1/users/me/preferences', { language })
        .pipe(finalize(() => this.saving = false))
        .subscribe({ next: () => this.apply(language), error: () => {
          this.error = translate('Could not save language. Please try again.');
        } });
    } else this.apply(language);
  }

  private apply(language: string): void {
    try { localStorage.setItem(STORAGE_KEY, language); }
    catch { this.error = translate('Could not save language. Please try again.'); return; }
    window.location.reload();
  }

}


function arabicCount(count: number, kind: 'plant' | 'tip' | 'day' | 'issue'): string {
  const forms = {
    plant: ['لا نباتات', 'نبتة واحدة', 'نبتتان', 'نباتات', 'نبتة'],
    tip: ['لا نصائح', 'نصيحة واحدة', 'نصيحتان', 'نصائح', 'نصيحة'],
    day: ['0 يوم', 'يوم واحد', 'يومان', 'أيام', 'يوم'],
    issue: ['لا مشكلات', 'مشكلة واحدة', 'مشكلتان', 'مشكلات', 'مشكلة'],
  }[kind];
  const plural = new Intl.PluralRules('ar').select(count);
  if (plural === 'zero') return forms[0];
  if (plural === 'one') return forms[1];
  if (plural === 'two') return forms[2];
  return `${count} ${forms[plural === 'few' ? 3 : 4]}`;
}

function arabicCountMessage(message: string, count: number): string | undefined {
  switch (message) {
    case '{0} plant{1}': return arabicCount(count, 'plant');
    case '{0} tip{1}': return arabicCount(count, 'tip');
    case 'You have {0} plant{1} under your care.': return `في رعايتك: ${arabicCount(count, 'plant')}.`;
    case 'Overdue by {0} day{1}': return `مدة التأخير: ${arabicCount(count, 'day')}`;
    case '· Overdue by {0} day{1}': return `· مدة التأخير: ${arabicCount(count, 'day')}`;
    case 'Next water: {0} day{1}': return `المدة حتى الري التالي: ${arabicCount(count, 'day')}`;
    default: return undefined;
  }
}
