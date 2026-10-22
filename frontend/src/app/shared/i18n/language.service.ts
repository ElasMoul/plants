import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { FR } from './fr';
import { HttpClient } from '@angular/common/http';
import { Optional } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { languageFromPath } from './public-routes';

/** The one frontend definition of the supported languages; English is the default. */
export const SUPPORTED_LANGUAGES = ['en', 'fr'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];
export const DEFAULT_LANGUAGE: Language = 'en';
const STORAGE_KEY = 'plantpal.language';
const EN_LABELS: Readonly<Record<string, string>> = {
  WATERING: 'Watering', LIGHT: 'Light', HUMIDITY: 'Humidity', TEMPERATURE: 'Temperature',
  FERTILIZING: 'Fertilizing', REPOTTING: 'Repotting', PRUNING: 'Pruning', PEST: 'Pest',
  SEASONAL: 'Seasonal', BEGINNER_TIP: 'Beginner tip',
  'placement.INDOOR': 'Indoor', 'placement.OUTDOOR': 'Outdoor', 'placement.GREENHOUSE': 'Greenhouse',
  'light.LOW': 'Low', 'light.MEDIUM': 'Medium', 'light.BRIGHT': 'Bright', 'light.DIRECT_SUN': 'Direct sun',
  'climate.TROPICAL': 'Tropical', 'climate.ARID': 'Arid', 'climate.TEMPERATE': 'Temperate',
  'climate.CONTINENTAL': 'Continental', 'climate.POLAR': 'Polar',
};
export const LANGUAGES: ReadonlyArray<{ code: Language; label: string; locale: string; direction: 'ltr' | 'rtl' }> = [
  { code: 'en', label: 'English', locale: 'en-US', direction: 'ltr' },
  { code: 'fr', label: 'Français', locale: 'fr-FR', direction: 'ltr' },
];

/** A supported code, or English for null, empty and unsupported values (e.g. a stored 'ar'). */
export function normalizeLanguage(value: unknown): Language {
  return SUPPORTED_LANGUAGES.find(code => code === value) ?? DEFAULT_LANGUAGE;
}

// Prerender only: the language of the URL being rendered (there is no window/localStorage).
let serverLanguage: Language | null = null;
export function setServerLanguage(language: Language | null): void { serverLanguage = language; }

export function readLanguage(): Language {
  if (typeof window === 'undefined') return serverLanguage ?? DEFAULT_LANGUAGE;
  // On a public /en/ or /fr/ URL the URL language wins over the stored preference.
  const fromUrl = languageFromPath(window.location.pathname);
  if (fromUrl) return fromUrl;
  try {
    return normalizeLanguage(localStorage.getItem(STORAGE_KEY));
  } catch { return DEFAULT_LANGUAGE; /* Storage unavailable: use the default. */ }
}

/**
 * A signed-in user's profile language wins over the device value: store it, and report whether
 * the page must reload to show it. Unsupported profile values (e.g. 'ar') resolve to English.
 */
export function adoptProfileLanguage(profileLanguage: string | null | undefined): boolean {
  if (!profileLanguage) return false;
  const language = normalizeLanguage(profileLanguage);
  try { localStorage.setItem(STORAGE_KEY, language); } catch { /* Keep the current device value. */ }
  return document.documentElement.lang !== language;
}

export function pluralSuffix(count: number): string {
  return new Intl.PluralRules(readLanguage()).select(count) === 'one' ? '' : 's';
}

/** Only call for application-owned copy, never for user or AI content. */
export function translate(message: string, values: readonly unknown[] = []): string {
  const summary = /^(\d+) issue\(s\)$/.exec(message);
  if (summary && readLanguage() === 'fr') return `${summary[1]} problème${pluralSuffix(Number(summary[1]))}`;
  // English is the source text: a missing or empty French entry falls back to it (never a blank).
  const text = (readLanguage() === 'fr' ? FR[message] : EN_LABELS[message]) || EN_LABELS[message] || message;
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
    if (this.saving || !SUPPORTED_LANGUAGES.some(code => code === language) || language === this.language) return;
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
    if (languageFromPath(window.location.pathname)) {
      // Public language URL: move to the matching prefix (or the root for languages without one).
      window.location.assign(`/${language}/`);
      return;
    }
    window.location.reload();
  }

}
