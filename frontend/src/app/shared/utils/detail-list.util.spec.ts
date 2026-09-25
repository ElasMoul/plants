import { parseDetailAsList } from './detail-list.util';

describe('parseDetailAsList', () => {
  it('returns null for empty input and plain prose', () => {
    expect(parseDetailAsList(null)).toBeNull();
    expect(parseDetailAsList(undefined)).toBeNull();
    expect(parseDetailAsList('')).toBeNull();
    expect(parseDetailAsList('Water deeply once a week and let the soil dry out.')).toBeNull();
  });

  it('splits an inline numbered procedure, keeping a lead-in as the intro', () => {
    expect(parseDetailAsList('Treat it as follows: 1. Remove leaves. 2. Spray neem oil. 3. Repeat weekly.')).toEqual({
      intro: 'Treat it as follows:',
      items: ['Remove leaves.', 'Spray neem oil.', 'Repeat weekly.'],
    });
  });

  it('splits a line-by-line numbered list', () => {
    expect(parseDetailAsList('1. Mix 5 ml per litre.\n2. Test on one leaf.')).toEqual({
      intro: null,
      items: ['Mix 5 ml per litre.', 'Test on one leaf.'],
    });
  });

  it('does not turn prose with sentence-ending numbers into a list', () => {
    expect(
      parseDetailAsList('Keep it between 18 and 24. Avoid drafts below 10. Mist the leaves in summer.'),
    ).toBeNull();
  });

  it('parses bullets, tolerating one lead-in line', () => {
    expect(parseDetailAsList('Apply as follows:\n- Dilute\n* Spray\n• Wait')).toEqual({
      intro: 'Apply as follows:',
      items: ['Dilute', 'Spray', 'Wait'],
    });
  });

  it('rejects prose that merely contains a stray dash or a single bullet', () => {
    expect(parseDetailAsList('Water weekly.\n- in summer\nMore prose here.\nAnd here.')).toBeNull();
    expect(parseDetailAsList('- only one bullet')).toBeNull();
  });
});
