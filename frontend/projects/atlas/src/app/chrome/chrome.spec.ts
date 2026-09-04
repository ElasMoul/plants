import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService, provideSharedCore } from '@plantpal/shared-core';
import { provideMockModeOff } from '../core/mock-mode';
import { DEFAULT_SETTINGS, structuredCloneish } from '../settings/settings.model';
import { SettingsStore } from '../settings/settings.store';
import type { WorldData, WorldMeta } from '../world/world.model';
import { WorldStore } from '../world/world.store';
import { Chrome, bellCountFor } from './chrome';

const NOW = '2026-09-03T09:12:00Z';
const OVERDUE = '2026-09-01T08:00:00Z';
const TODAY = '2026-09-03T18:00:00Z';

function meta(over: Partial<WorldMeta> = {}): WorldMeta {
  return {
    syncedAt: NOW,
    reminders: [],
    dueReminders: [
      { id: 601, nextDueAt: OVERDUE, plantId: 1, label: 'Water · Office Fig' },
      { id: 602, nextDueAt: TODAY, plantId: 2, label: 'Water · Studio Fig' },
      { id: 702, nextDueAt: TODAY, plantId: 1, label: 'Pest check · Office Fig', treatmentPlanId: 201 },
    ],
    plantsIndex: [],
    treatmentsIndex: {},
    scansByPlant: {},
    hasPendingDescription: false,
    failures: [],
    ...over,
  };
}

const settingsWith = (fn: (s: ReturnType<typeof structuredCloneish>) => void) => {
  const s = structuredCloneish(DEFAULT_SETTINGS);
  fn(s);
  return s;
};

describe('bellCountFor (S6 — the arrival counts before it moves)', () => {
  it('counts every due reminder, or only the late ones, or nothing', () => {
    expect(bellCountFor(meta(), DEFAULT_SETTINGS, {}, NOW)).toBe(2);
    expect(
      bellCountFor(meta(), settingsWith(s => (s.notifications.bellCounts = 'overdue')), {}, NOW),
    ).toBe(1);
    expect(
      bellCountFor(meta(), settingsWith(s => (s.notifications.bellCounts = 'none')), {}, NOW),
    ).toBe(0);
  });

  it('leaves treatment steps to their course unless the reader asked for them', () => {
    expect(
      bellCountFor(
        meta(),
        settingsWith(s => (s.data.stepReminders = 'also-in-reminders')),
        {},
        NOW,
      ),
    ).toBe(3);
  });

  it('excludes what is snoozed on this device and what has been seen', () => {
    expect(bellCountFor(meta(), DEFAULT_SETTINGS, { 601: '2026-09-04T09:00:00Z' }, NOW)).toBe(1);
    // a snooze whose moment has passed is not a snooze
    expect(bellCountFor(meta(), DEFAULT_SETTINGS, { 601: '2026-09-02T09:00:00Z' }, NOW)).toBe(2);
    expect(
      bellCountFor(
        meta(),
        settingsWith(s => (s.notifications.seenAt = '2026-09-02T00:00:00Z')),
        {},
        NOW,
      ),
    ).toBe(1);
  });

  it('is silent inside quiet hours', () => {
    const night = '2026-09-03T22:30:00';
    expect(bellCountFor(meta(), DEFAULT_SETTINGS, {}, night)).toBe(0);
    expect(
      bellCountFor(meta(), settingsWith(s => (s.profile.quietHours = 'off')), {}, night),
    ).toBe(2);
  });

  it('counts nothing when the loader has learned nothing yet', () => {
    expect(bellCountFor(undefined, DEFAULT_SETTINGS, {}, NOW)).toBe(0);
  });
});

/** Two nodes, one vein — enough geography for the bell to have a distance. */
function world(): WorldData {
  return {
    initialFocus: 'n-garden',
    edges: [['n-garden', 'n-reminders']],
    meta: meta(),
    nodes: [
      { id: 'n-garden', cell: { col: 0, row: 6 }, kind: 'collection', kindLabel: 'Garden', glyph: '♣', name: 'My garden', recap: '2 plants' },
      { id: 'n-reminders', cell: { col: 2, row: 6 }, kind: 'journal', kindLabel: 'Reminders', glyph: '◷', name: 'Reminders', recap: '2 due today' },
    ],
  };
}

describe('Chrome — the bell as an arrival (C16/C21)', () => {
  let fixture: ComponentFixture<Chrome>;
  let store: WorldStore;
  let settings: SettingsStore;

  beforeEach(() => {
    localStorage.clear();
    // travel settles synchronously only when motion is reduced (no rAF in jsdom)
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: () => ({ matches: true, addEventListener: () => undefined, removeEventListener: () => undefined }),
    });
    TestBed.configureTestingModule({
      imports: [Chrome],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMockModeOff(),
        ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
        { provide: AuthService, useValue: { isLoggedIn: () => true, getCurrentUser: () => ({ firstName: 'Sam' }), getToken: () => null } },
      ],
    });
    store = TestBed.inject(WorldStore);
    settings = TestBed.inject(SettingsStore);
    settings.set('profile.quietHours', 'off'); // the test clock must not be night-dependent
    store.setWorld(world());
    fixture = TestBed.createComponent(Chrome);
    fixture.detectChanges();
  });

  afterEach(() => localStorage.clear());

  const bell = (): HTMLButtonElement =>
    fixture.nativeElement.querySelector('#bell') as HTMLButtonElement;

  it('wears the count, and wears nothing at all when there is none', () => {
    expect(bell().querySelector('.ch-count')!.textContent!.trim()).toBe('2');
    settings.set('notifications.bellCounts', 'none');
    fixture.detectChanges();
    expect(bell().querySelector('.ch-count')).toBeNull();
  });

  it('announces the count and the distance BEFORE it travels', async () => {
    const spoken: string[] = [];
    const go = jest.spyOn(store, 'go').mockImplementation(() => {
      spoken.push(`GO:${store.announcement()}`);
    });
    bell().click();
    // the sentence lands first, and the travel waits a tick for it
    expect(go).not.toHaveBeenCalled();
    expect(store.announcement()).toContain('2 due, 1 vein from here');
    expect(store.announcement()).toContain('nothing on the way opens');
    await new Promise(r => setTimeout(r, 0));
    expect(go).toHaveBeenCalledWith('n-reminders');
    expect(spoken[0]).toContain('2 due, 1 vein from here');
  });

  it('does not move when the bell points at where you already are', () => {
    store.go('n-reminders');
    const go = jest.spyOn(store, 'go');
    bell().click();
    expect(go).not.toHaveBeenCalled();
    expect(store.announcement()).toBe('2 due. You are already there.');
    expect(store.focusId()).toBe('n-reminders');
  });

  it('says so rather than travelling when the target is not on this board', () => {
    settings.set('notifications.bellTarget', 'n-today');
    fixture.detectChanges();
    const go = jest.spyOn(store, 'go');
    bell().click();
    expect(go).not.toHaveBeenCalled();
    expect(store.announcement()).toBe('That place is not on this board yet.');
  });

  it('the offline bar names the moment this board was last true', () => {
    const bar = fixture.nativeElement.querySelector('#offline-bar span') as HTMLElement;
    expect(bar.textContent).toContain(store.readAtLabel());
    expect(bar.textContent).toContain('changes will be queued');
  });
});
