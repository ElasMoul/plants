import { CLIMATE_ZONES, COUNTRY_CODES, countryOptions } from './region';

describe('region list (PLA-107)', () => {
  it('is a fixed list of ISO country codes and five climate zones', () => {
    expect(COUNTRY_CODES.every(code => /^[A-Z]{2}$/.test(code))).toBe(true);
    expect(new Set(COUNTRY_CODES).size).toBe(COUNTRY_CODES.length);
    expect(CLIMATE_ZONES).toEqual(['TROPICAL', 'ARID', 'TEMPERATE', 'CONTINENTAL', 'POLAR']);
  });

  it('localises country names and falls back to codes', () => {
    const fr = countryOptions('fr');
    expect(fr.find(c => c.code === 'DE')?.name).toBe('Allemagne');
    expect(fr).toHaveLength(COUNTRY_CODES.length);
  });
});
