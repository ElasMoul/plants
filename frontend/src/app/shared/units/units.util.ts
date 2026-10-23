export type UnitSystem = 'METRIC' | 'IMPERIAL';
export type UnitKind = 'temperature' | 'length' | 'volume';

const CM_PER_INCH = 2.54;
const FEET_PER_METRE = 3.28084;
const ML_PER_FL_OZ = 29.5735;
const LITRES_PER_GALLON = 3.78541;

const round = (value: number, digits: number): number => {
  const factor = 10 ** digits;
  return Math.round((value + Number.EPSILON) * factor) / factor;
};

/** One decimal below 10, whole numbers above - keeps converted advice readable. */
const compact = (value: number): number => (Math.abs(value) < 10 ? round(value, 1) : Math.round(value));

export const celsiusToFahrenheit = (c: number): number => Math.round((c * 9) / 5 + 32);
export const cmToInches = (cm: number): number => compact(cm / CM_PER_INCH);
export const metresToFeet = (m: number): number => compact(m * FEET_PER_METRE);
export const mlToFlOz = (ml: number): number => compact(ml / ML_PER_FL_OZ);
export const litresToGallons = (l: number): number => round(l / LITRES_PER_GALLON, 2);

/** Format a canonical metric value in the chosen system, e.g. 18 temperature IMPERIAL gives `64 °F`. */
export function formatUnit(value: number | null | undefined, kind: UnitKind, system: UnitSystem, small = true): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '';
  const imperial = system === 'IMPERIAL';
  switch (kind) {
    case 'temperature': return imperial ? `${celsiusToFahrenheit(value)} °F` : `${round(value, 1)} °C`;
    case 'length':
      if (small) return imperial ? `${cmToInches(value)} in` : `${round(value, 1)} cm`;
      return imperial ? `${metresToFeet(value)} ft` : `${round(value, 2)} m`;
    case 'volume':
      if (small) return imperial ? `${mlToFlOz(value)} fl oz` : `${round(value, 1)} ml`;
      return imperial ? `${litresToGallons(value)} gal` : `${round(value, 2)} l`;
  }
}

const NUM = String.raw`\d+(?:[.,]\d+)?`;
const RANGE = String.raw`(${NUM})(?:\s*(?:[–—-]|to)\s*(${NUM}))?`;
const parse = (raw: string): number => Number(raw.replace(',', '.'));

function convertRange(
  match: string, a: string, b: string | undefined, convert: (n: number) => number, suffix: string,
): string {
  const decimalComma = a.includes(',') || (b?.includes(',') ?? false);
  const out = (n: number) => (decimalComma ? String(n).replace('.', ',') : String(n));
  const first = out(convert(parse(a)));
  if (b === undefined) return `${first} ${suffix}`;
  const sep = /to/.test(match) ? ' to ' : '–';
  return `${first}${sep}${out(convert(parse(b)))} ${suffix}`;
}

/**
 * Rewrites metric quantities in free text (AI care advice) to imperial. Metric text is returned
 * untouched; anything that does not match a known pattern is left as written.
 */
export function convertUnitsInText(text: string | null | undefined, system: UnitSystem): string {
  if (!text) return text ?? '';
  if (system !== 'IMPERIAL') return text;
  return text
    .replace(new RegExp(`${RANGE}\\s*°\\s*C(?![A-Za-z])`, 'g'),
      (m, a, b) => convertRange(m, a, b, celsiusToFahrenheit, '°F'))
    .replace(new RegExp(`${RANGE}\\s*cm(?![A-Za-z])`, 'g'),
      (m, a, b) => convertRange(m, a, b, cmToInches, 'in'))
    .replace(new RegExp(`${RANGE}\\s*m(?![A-Za-z²³])`, 'g'),
      (m, a, b) => convertRange(m, a, b, metresToFeet, 'ft'))
    .replace(new RegExp(`${RANGE}\\s*(?:ml|mL|ML)(?![A-Za-z])`, 'g'),
      (m, a, b) => convertRange(m, a, b, mlToFlOz, 'fl oz'))
    .replace(new RegExp(`${RANGE}\\s*(?:litres?|liters?|[lL])(?![A-Za-z])`, 'g'),
      (m, a, b) => convertRange(m, a, b, litresToGallons, 'gal'));
}
