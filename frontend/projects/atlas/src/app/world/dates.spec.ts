import {
  agoLabel,
  careLabel,
  dateLabel,
  daysUntil,
  dueLine,
  isDue,
  timeLabel,
  withinQuietHours,
  wordNumber,
} from './dates';
import { CARE_TYPES } from './world.dto';

/** Local-clock ISO strings, so every assertion holds in any time zone. */
const BASE = new Date(2026, 8, 4, 9, 12, 0); // 2026-09-04 09:12 local
function at(days: number, hours = 0, minutes = 0): string {
  const d = new Date(BASE);
  d.setDate(d.getDate() + days);
  d.setHours(hours, minutes, 0, 0);
  return d.toISOString();
}
const NOW = BASE.toISOString();
const RELATIVE = { dueWindow: 'server-day' as const, dateStyle: 'relative' as const };

describe('dates — the atlas date vocabulary', () => {
  describe('daysUntil', () => {
    it('counts calendar days under server-day', () => {
      expect(daysUntil(at(0, 23, 30), NOW, 'server-day')).toBe(0);
      expect(daysUntil(at(1, 0, 5), NOW, 'server-day')).toBe(1);
      expect(daysUntil(at(-2, 8), NOW, 'server-day')).toBe(-2);
    });

    it('counts whole elapsed days under rolling-24h', () => {
      // 23:30 tonight is under 24h away, so it has not turned a day yet
      expect(daysUntil(at(0, 23, 30), NOW, 'rolling-24h')).toBe(0);
      expect(daysUntil(at(1, 23, 30), NOW, 'rolling-24h')).toBe(1);
      // two hours ago is not yet a day overdue
      expect(daysUntil(at(0, 7, 12), NOW, 'rolling-24h')).toBe(0);
      expect(daysUntil(at(-2, 8), NOW, 'rolling-24h')).toBe(-2);
    });

    it('isDue is true at or past the moment, under either rule', () => {
      expect(isDue(at(0, 7), NOW, 'server-day')).toBe(true);
      expect(isDue(at(3), NOW, 'server-day')).toBe(false);
      expect(isDue(at(-1), NOW, 'rolling-24h')).toBe(true);
    });
  });

  describe('dueLine', () => {
    const table: [string, string, string][] = [
      ['overdue by one day', at(-1, 9), 'Overdue 1 day'],
      ['overdue by two days', at(-2, 9), 'Overdue 2 days'],
      ['today at midnight', at(0), 'Today'],
      ['today with an hour', at(0, 18), 'Today, 18:00'],
      ['tomorrow', at(1), 'Tomorrow'],
      ['in three days', at(3), 'In 3 days'],
      ['far out', at(40), dateLabel(at(40))],
    ];
    for (const [name, iso, expected] of table) {
      it(`speaks ${name} as "${expected}"`, () => {
        expect(dueLine(iso, NOW, RELATIVE)).toBe(expected);
      });
    }

    it('gives the same words under rolling-24h for whole-day offsets', () => {
      const rolling = { dueWindow: 'rolling-24h' as const, dateStyle: 'relative' as const };
      expect(dueLine(at(3, 9, 12), NOW, rolling)).toBe('In 3 days');
      expect(dueLine(at(-2, 9, 12), NOW, rolling)).toBe('Overdue 2 days');
    });

    it("trusts the server's daysOverdue under server-day only", () => {
      expect(dueLine(at(0, 18), NOW, RELATIVE, 3)).toBe('Overdue 3 days');
      const rolling = { dueWindow: 'rolling-24h' as const, dateStyle: 'relative' as const };
      expect(dueLine(at(0, 18), NOW, rolling, 3)).toBe('Today, 18:00');
    });

    it('prints a real date when the style is absolute, marking the past', () => {
      const absolute = { dueWindow: 'server-day' as const, dateStyle: 'absolute' as const };
      expect(dueLine(at(3), NOW, absolute)).toBe(dateLabel(at(3)));
      expect(dueLine(at(-2), NOW, absolute)).toBe(`${dateLabel(at(-2))} · overdue`);
    });

    it('never says loading and never trails an ellipsis', () => {
      for (const [, iso] of [[0, at(0)], [1, at(-5)], [2, at(9)]] as [number, string][]) {
        const line = dueLine(iso, NOW, RELATIVE);
        expect(line.toLowerCase()).not.toContain('loading');
        expect(line).not.toMatch(/\.\.\.|…/);
      }
    });
  });

  describe('agoLabel', () => {
    it('speaks the recent past and dates the rest', () => {
      expect(agoLabel(at(0, 8), NOW)).toBe('Today');
      expect(agoLabel(at(-1, 8), NOW)).toBe('Yesterday');
      expect(agoLabel(at(-3, 8), NOW)).toBe('3 days ago');
      expect(agoLabel(at(-40, 8), NOW)).toBe(dateLabel(at(-40, 8)));
      expect(agoLabel(at(-3, 8), NOW, 'absolute')).toBe(dateLabel(at(-3, 8)));
    });
  });

  describe('dateLabel / timeLabel', () => {
    it('write a short date and a 24-hour wall time', () => {
      expect(dateLabel(new Date(2026, 6, 24, 13, 5).toISOString())).toBe('Jul 24');
      expect(timeLabel(new Date(2026, 6, 24, 9, 12).toISOString())).toBe('09:12');
    });
    it('answers with an em-dash for an unparseable instant', () => {
      expect(dateLabel('not a date')).toBe('—');
      expect(timeLabel('not a date')).toBe('—');
    });
  });

  describe('careLabel', () => {
    it('maps all ten care types to a gardener word', () => {
      const labels = CARE_TYPES.map(careLabel);
      expect(labels).toHaveLength(10);
      expect(new Set(labels).size).toBe(10);
      expect(labels).toEqual(expect.arrayContaining(['Water', 'Feed', 'Repot', 'Prune', 'Pest check']));
      expect(labels.some(l => l === l.toUpperCase() && l.length > 4)).toBe(false);
    });
    it('falls back to a word, never the enum', () => {
      expect(careLabel('SOMETHING_NEW')).toBe('Care');
    });
  });

  describe('wordNumber', () => {
    it('writes one to ten as words and counts the rest', () => {
      expect([1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(wordNumber)).toEqual([
        'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
      ]);
      expect(wordNumber(11)).toBe('11');
      expect(wordNumber(0)).toBe('Zero');
    });
  });

  describe('withinQuietHours', () => {
    it('covers the wrap past midnight', () => {
      const q = '21:00-07:30';
      expect(withinQuietHours(new Date(2026, 8, 4, 22, 0).toISOString(), q)).toBe(true);
      expect(withinQuietHours(new Date(2026, 8, 4, 2, 0).toISOString(), q)).toBe(true);
      expect(withinQuietHours(new Date(2026, 8, 4, 7, 29).toISOString(), q)).toBe(true);
      expect(withinQuietHours(new Date(2026, 8, 4, 7, 30).toISOString(), q)).toBe(false);
      expect(withinQuietHours(new Date(2026, 8, 4, 12, 0).toISOString(), q)).toBe(false);
    });
    it('handles a same-day window and "off"', () => {
      expect(withinQuietHours(new Date(2026, 8, 4, 13, 0).toISOString(), '12:00-14:00')).toBe(true);
      expect(withinQuietHours(new Date(2026, 8, 4, 22, 0).toISOString(), 'off')).toBe(false);
    });
  });
});
