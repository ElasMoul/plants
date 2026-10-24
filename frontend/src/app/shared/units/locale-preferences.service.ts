import { Injectable, Injector, signal } from '@angular/core';
import { UserPreferences, UserService } from '@plantpal/shared-core';
import { LANGUAGES, readLanguage } from '../i18n/language.service';
import { UnitSystem } from './units.util';

export function detectTimeZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

/** Browser locale when it matches the active app language, else that language default. */
export function activeLocale(): string {
  const language = readLanguage();
  const browser = typeof navigator !== 'undefined' ? navigator.language : '';
  if (browser && browser.toLowerCase().startsWith(language)) return browser;
  return LANGUAGES.find(l => l.code === language)?.locale ?? 'en-US';
}

/** Units and time zone of the signed-in user (PLA-132); metric and the browser zone until loaded. */
@Injectable({ providedIn: 'root' })
export class LocalePreferencesService {
  readonly unitSystem = signal<UnitSystem>('METRIC');
  readonly timeZone = signal<string | null>(null);

  // Resolved lazily so pipes that only read the signals do not need the HTTP stack.
  constructor(private readonly injector: Injector) {}

  private get userService(): UserService {
    return this.injector.get(UserService);
  }

  load(): void {
    this.userService.getPreferences().subscribe({
      next: res => this.apply(res.data),
      error: () => undefined,
    });
  }

  apply(prefs: UserPreferences): void {
    this.unitSystem.set(prefs.effectiveUnitSystem ?? prefs.unitSystem ?? 'METRIC');
    this.timeZone.set(prefs.timeZone ?? null);
    const detected = detectTimeZone();
    if (detected && detected !== prefs.timeZone) {
      this.userService.updateTimeZone(detected).subscribe({
        next: res => this.timeZone.set(res.data.timeZone ?? detected),
        error: () => undefined,
      });
    }
  }
}
