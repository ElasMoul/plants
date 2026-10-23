import { convertUnitsInText, formatUnit } from './units.util';
import { formatLocaleDate, formatLocaleNumber } from './unit.pipes';

describe('units (PLA-132)', () => {
  describe('convertUnitsInText', () => {
    it('leaves text unchanged for metric', () => {
      const text = 'Keep at 18–24 °C, pot 30 cm wide, water with 250 ml.';
      expect(convertUnitsInText(text, 'METRIC')).toBe(text);
    });

    it('converts temperature ranges, lengths and volumes for imperial', () => {
      expect(convertUnitsInText('Keep at 18–24 °C.', 'IMPERIAL')).toBe('Keep at 64–75 °F.');
      expect(convertUnitsInText('Use a pot 30 cm wide.', 'IMPERIAL')).toBe('Use a pot 12 in wide.');
      expect(convertUnitsInText('Give 250 ml every week.', 'IMPERIAL')).toBe('Give 8.5 fl oz every week.');
      expect(convertUnitsInText('Grows to 2 m tall.', 'IMPERIAL')).toBe('Grows to 6.6 ft tall.');
      expect(convertUnitsInText('Use 2 litres of water.', 'IMPERIAL')).toBe('Use 0.53 gal of water.');
    });

    it('handles single values, "to" ranges and decimal commas', () => {
      expect(convertUnitsInText('Never below 10°C', 'IMPERIAL')).toBe('Never below 50 °F');
      expect(convertUnitsInText('15 to 20 °C', 'IMPERIAL')).toBe('59 to 68 °F');
      expect(convertUnitsInText('0,5 l', 'IMPERIAL')).toBe('0,13 gal');
    });

    it('does not touch unmatched text or other words ending in m/l', () => {
      const text = 'Water the plant in the morning, then mist. Bloom 3 times; 5 mm gap; 10 mL';
      const out = convertUnitsInText(text, 'IMPERIAL');
      expect(out).toContain('Water the plant in the morning, then mist.');
      expect(out).toContain('3 times');
      expect(out).toContain('5 mm gap');
      expect(out).toContain('0.3 fl oz');
    });

    it('handles empty values', () => {
      expect(convertUnitsInText(null, 'IMPERIAL')).toBe('');
      expect(convertUnitsInText('', 'IMPERIAL')).toBe('');
    });
  });

  describe('formatUnit', () => {
    it('formats in both systems', () => {
      expect(formatUnit(18, 'temperature', 'METRIC')).toBe('18 °C');
      expect(formatUnit(18, 'temperature', 'IMPERIAL')).toBe('64 °F');
      expect(formatUnit(30, 'length', 'IMPERIAL')).toBe('12 in');
      expect(formatUnit(30, 'length', 'METRIC')).toBe('30 cm');
      expect(formatUnit(250, 'volume', 'IMPERIAL')).toBe('8.5 fl oz');
      expect(formatUnit(1, 'length', 'IMPERIAL', false)).toBe('3.3 ft');
      expect(formatUnit(3.8, 'volume', 'IMPERIAL', false)).toBe('1 gal');
      expect(formatUnit(null, 'volume', 'METRIC')).toBe('');
    });
  });

  describe('locale formatting', () => {
    const instant = '2026-03-08T01:30:00Z';

    it('orders dates by locale', () => {
      expect(formatLocaleDate(instant, 'shortDate', 'en-US', 'UTC')).toBe('3/8/26');
      expect(formatLocaleDate(instant, 'shortDate', 'en-GB', 'UTC')).toBe('08/03/2026');
    });

    it('applies the time zone', () => {
      expect(formatLocaleDate(instant, 'shortDate', 'en-US', 'America/New_York')).toBe('3/7/26');
      expect(formatLocaleDate(instant, 'shortTime', 'en-GB', 'Europe/Berlin')).toBe('02:30');
    });

    it('falls back to the default zone for an invalid one and blanks bad input', () => {
      expect(formatLocaleDate(instant, 'mediumDate', 'en-US', 'Nope/Zone')).not.toBe('');
      expect(formatLocaleDate(null, 'mediumDate', 'en-US', null)).toBe('');
      expect(formatLocaleDate('garbage', 'mediumDate', 'en-US', null)).toBe('');
    });

    it('formats numbers per locale', () => {
      expect(formatLocaleNumber(1234.5, 'en-US')).toBe('1,234.5');
      expect(formatLocaleNumber(1234.5, 'de-DE')).toBe('1.234,5');
      expect(formatLocaleNumber(12.345, 'en-US', '1.0-0')).toBe('12');
    });
  });
});
