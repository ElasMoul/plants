import { TestBed } from '@angular/core/testing';
import { SettingsStore } from './settings.store';
import { DEFAULT_SETTINGS, SETTINGS_KEY } from './settings.model';

function make(): SettingsStore {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [SettingsStore] });
  return TestBed.inject(SettingsStore);
}

describe('SettingsStore', () => {
  beforeEach(() => localStorage.clear());

  it('uses the defaults when storage is empty', () => {
    expect(make().settings()).toEqual(DEFAULT_SETTINGS);
  });

  it('persists only the difference from the defaults', () => {
    const s = make();
    s.set('general.pollIntervalMs', 20000);
    // data.source is always written, default or not, so an explicit "live"
    // choice survives a reload (mock-mode reads this blob).
    expect(JSON.parse(localStorage.getItem(SETTINGS_KEY)!)).toEqual({
      general: { pollIntervalMs: 20000 },
      data: { source: 'live' },
    });
    expect(make().get('general.pollIntervalMs')).toBe(20000);
  });

  it('drops unknown keys and out-of-range values', () => {
    localStorage.setItem(
      SETTINGS_KEY,
      JSON.stringify({ general: { pollIntervalMs: 9, nonsense: 1 }, data: { source: 'x' } }),
    );
    const s = make();
    expect(s.get('general.pollIntervalMs')).toBe(8000);
    expect(s.get('general.nonsense')).toBeUndefined();
    expect(s.get('data.source')).toBe('live');
  });

  it('ignores malformed JSON', () => {
    localStorage.setItem(SETTINGS_KEY, '{not json');
    expect(make().settings()).toEqual(DEFAULT_SETTINGS);
  });

  it('reset restores the defaults and persists them', () => {
    const s = make();
    s.set('care.askForNotes', true);
    s.reset();
    expect(s.settings()).toEqual(DEFAULT_SETTINGS);
    expect(JSON.parse(localStorage.getItem(SETTINGS_KEY)!)).toEqual({ data: { source: 'live' } });
  });

  it('open, change and cancel restores the snapshot taken at open', () => {
    const s = make();
    s.set('appearance.palette', 'terrarium');
    s.open();
    s.set('appearance.palette', 'late-bench');
    s.set('care.defaultFrequencyDays', 14);
    expect(s.get('appearance.palette')).toBe('late-bench');
    s.cancel();
    expect(s.get('appearance.palette')).toBe('terrarium');
    expect(s.get('care.defaultFrequencyDays')).toBe(7);
  });

  it('save keeps the changes made since open', () => {
    const s = make();
    s.open();
    s.set('appearance.palette', 'terrarium');
    s.save();
    s.cancel();
    expect(s.get('appearance.palette')).toBe('terrarium');
  });

  it('assemblySnapshot is a plain object', () => {
    const s = make();
    s.set('general.dateStyle', 'absolute');
    const snap = s.assemblySnapshot();
    expect(Object.getPrototypeOf(snap)).toBe(Object.prototype);
    expect(snap.dateStyle).toBe('absolute');
    (snap as { dateStyle: string }).dateStyle = 'relative';
    expect(s.settings().general.dateStyle).toBe('absolute');
  });

  it('does not throw when localStorage.setItem throws', () => {
    const s = make();
    const setItem = jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => s.set('care.askForNotes', true)).not.toThrow();
    expect(s.get('care.askForNotes')).toBe(true);
    setItem.mockRestore();
  });
});
