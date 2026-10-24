import { Pipe, PipeTransform } from '@angular/core';
import { LocalePreferencesService, activeLocale } from './locale-preferences.service';
import { UnitKind, formatUnit } from './units.util';

@Pipe({ name: 'unit', standalone: true, pure: false })
export class UnitPipe implements PipeTransform {
  constructor(private readonly prefs: LocalePreferencesService) {}
  transform(value: number | null | undefined, kind: UnitKind, small = true): string {
    return formatUnit(value, kind, this.prefs.unitSystem(), small);
  }
}

const DATE_STYLES: Record<string, Intl.DateTimeFormatOptions> = {
  short: { dateStyle: 'short', timeStyle: 'short' },
  medium: { dateStyle: 'medium', timeStyle: 'medium' },
  long: { dateStyle: 'long', timeStyle: 'short' },
  full: { dateStyle: 'full', timeStyle: 'short' },
  shortDate: { dateStyle: 'short' },
  mediumDate: { dateStyle: 'medium' },
  longDate: { dateStyle: 'long' },
  fullDate: { dateStyle: 'full' },
  shortTime: { timeStyle: 'short' },
  mediumTime: { timeStyle: 'medium' },
};

/** Format a date with Intl in the given locale and the stored time zone (browser zone if none). */
export function formatLocaleDate(
  value: string | number | Date | null | undefined, style: string, locale: string, timeZone: string | null,
): string {
  if (value === null || value === undefined || value === '') return '';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const options = DATE_STYLES[style] ?? DATE_STYLES['mediumDate'];
  try {
    return new Intl.DateTimeFormat(locale, { ...options, ...(timeZone ? { timeZone } : {}) }).format(date);
  } catch {
    return new Intl.DateTimeFormat(locale, options).format(date);
  }
}

export function formatLocaleNumber(value: number | null | undefined, locale: string, digits = '1.0-3'): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '';
  const m = /^(\d+)\.(\d+)-(\d+)$/.exec(digits);
  return new Intl.NumberFormat(locale, {
    minimumIntegerDigits: m ? Number(m[1]) : 1,
    minimumFractionDigits: m ? Number(m[2]) : 0,
    maximumFractionDigits: m ? Number(m[3]) : 3,
  }).format(value);
}

@Pipe({ name: 'localeDate', standalone: true, pure: false })
export class LocaleDatePipe implements PipeTransform {
  constructor(private readonly prefs: LocalePreferencesService) {}
  transform(value: string | number | Date | null | undefined, style = 'mediumDate'): string {
    return formatLocaleDate(value, style, activeLocale(), this.prefs.timeZone());
  }
}

@Pipe({ name: 'localeNumber', standalone: true, pure: false })
export class LocaleNumberPipe implements PipeTransform {
  transform(value: number | null | undefined, digits = '1.0-3'): string {
    return formatLocaleNumber(value, activeLocale(), digits);
  }
}
