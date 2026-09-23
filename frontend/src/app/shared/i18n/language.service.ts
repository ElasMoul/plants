import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { FR } from './fr';

export type Language = 'en' | 'fr';
const STORAGE_KEY = 'plantpal.language';
const EN_LABELS: Readonly<Record<string, string>> = {
  WATERING: 'Watering', LIGHT: 'Light', HUMIDITY: 'Humidity', TEMPERATURE: 'Temperature',
  FERTILIZING: 'Fertilizing', REPOTTING: 'Repotting', PRUNING: 'Pruning', PEST: 'Pest',
  SEASONAL: 'Seasonal', BEGINNER_TIP: 'Beginner tip',
};
export const LANGUAGES: ReadonlyArray<{ code: Language; label: string; locale: string; direction: 'ltr' | 'rtl' }> = [
  { code: 'en', label: 'English', locale: 'en-US', direction: 'ltr' },
  { code: 'fr', label: 'Français', locale: 'fr-FR', direction: 'ltr' },
];

export function readLanguage(): Language {
  try { return localStorage.getItem(STORAGE_KEY) === 'fr' ? 'fr' : 'en'; }
  catch { return 'en'; }
}

export function pluralSuffix(count: number): string {
  return new Intl.PluralRules(readLanguage()).select(count) === 'one' ? '' : 's';
}

/** Only call for application-owned copy, never for user or AI content. */
export function translate(message: string, values: readonly unknown[] = []): string {
  const summary = /^(\d+) issue\(s\)$/.exec(message);
  if (summary && readLanguage() === 'fr') return `${summary[1]} problème${pluralSuffix(Number(summary[1]))}`;
  const text = readLanguage() === 'fr' ? FR[message] ?? message : EN_LABELS[message] ?? message;
  return text.replace(/\{(\d+)\}/g, (match, index: string) =>
    Number(index) < values.length ? String(values[Number(index)] ?? '') : match);
}

@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly languages = LANGUAGES;
  readonly language = readLanguage();
  readonly locale = LANGUAGES.find(item => item.code === this.language)!.locale;

  constructor(@Inject(DOCUMENT) document: Document) {
    document.documentElement.lang = this.language;
    document.documentElement.dir = LANGUAGES.find(item => item.code === this.language)!.direction;
  }

  select(language: string): void {
    if (!LANGUAGES.some(item => item.code === language) || language === this.language) return;
    try { localStorage.setItem(STORAGE_KEY, language); }
    catch { return; }
    // Reinitialize Angular and Material locale providers, including lazy-loaded calendars.
    window.location.reload();
  }
}
