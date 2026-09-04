import { DEFAULT_SETTINGS, structuredCloneish } from '../settings/settings.model';
import type { WorldMeta } from '../world/world.model';
import { FIXTURE_ACTIONS, actionsFor } from './actions-for';

function meta(over: Partial<WorldMeta> = {}): WorldMeta {
  return {
    syncedAt: '2026-09-04T09:00:00Z',
    reminders: [
      {
        id: 601,
        plantId: 1,
        careType: 'WATERING',
        frequencyDays: 7,
        nextDueAt: '2026-09-05T09:00:00Z',
        enabled: true,
        recurring: true,
      },
    ],
    dueReminders: [],
    plantsIndex: [{ id: 1, nickname: 'Office Fig' }],
    treatmentsIndex: {
      301: { plantId: 1, status: 'IN_PROGRESS', planId: 201, nextStepId: 702, paused: false },
      302: { plantId: 2, status: 'DRAFT', paused: false },
      304: { plantId: 6, status: 'COMPLETED', planId: 202, paused: false },
      305: { plantId: 3, status: 'IN_PROGRESS', planId: 203, paused: true },
    },
    scansByPlant: { 1: 501 },
    hasPendingDescription: false,
    failures: [],
    ...over,
  };
}

const S = DEFAULT_SETTINGS;

describe('actionsFor — the Actions rail', () => {
  it('keeps the pinned prototype map verbatim on the fixture board', () => {
    for (const [id, labels] of Object.entries(FIXTURE_ACTIONS)) {
      expect(actionsFor(id, undefined, S)).toEqual(labels);
    }
    expect(actionsFor('n-nowhere', undefined, S)).toEqual([]);
  });

  it('offers a plant the care it can actually take', () => {
    expect(actionsFor('n-plant-1', meta(), S)).toEqual([
      'Water plant',
      'Fertilize',
      'Add note',
      'Log a watering',
      'Scan leaf (AI)',
    ]);
  });

  it('a plant with no watering schedule is offered one instead of a false press', () => {
    expect(actionsFor('n-plant-9', meta(), S)[0]).toBe('Set a watering schedule');
  });

  it('a course is keyed by its status', () => {
    expect(actionsFor('n-treatment-302', meta(), S)).toEqual(['Craft the treatment plan']);
    expect(actionsFor('n-treatment-301', meta(), S)).toEqual([
      'Mark today done',
      'Pause this course',
      'Finish this course',
    ]);
    expect(actionsFor('n-treatment-305', meta(), S)).toContain('Resume this course');
    expect(actionsFor('n-treatment-304', meta(), S)).toEqual([]);
    expect(actionsFor('n-treatment-999', meta(), S)).toEqual([]);
  });

  it('drops the device-local labels when their setting is off', () => {
    const off = structuredCloneish(S);
    off.reminders.snooze = 'off';
    off.treatment.pause = 'off';
    expect(actionsFor('n-reminders', meta(), off)).toEqual(['Add a reminder']);
    expect(actionsFor('n-treatment-301', meta(), off)).toEqual(['Mark today done', 'Finish this course']);
  });

  it('offers snooze on the reminders hub while the setting is local', () => {
    expect(actionsFor('n-reminders', meta(), S)).toEqual(['Add a reminder', 'Snooze all']);
  });

  it('the hubs get their own live verbs', () => {
    expect(actionsFor('n-garden', meta(), S)).toEqual(['Add new plant', 'Water all', 'Add note']);
    expect(actionsFor('n-care', meta(), S)).toEqual(['Log a watering', 'Add a reminder']);
    expect(actionsFor('n-journal', meta(), S)).toEqual(['Add note']);
    expect(actionsFor('n-log-9', meta(), S)).toEqual(['Add note']);
    expect(actionsFor('n-treatments', meta(), S)).toEqual(['Start a treatment plan']);
    expect(actionsFor('n-scan-5', meta(), S)).toEqual(['Try the scan again']);
    expect(actionsFor('n-species-3', meta(), S)).toEqual(['Add a species by hand']);
  });

  it('leaves Today, the account and the ask node without a rail', () => {
    expect(actionsFor('n-today', meta(), S)).toEqual([]);
    expect(actionsFor('n-account', meta(), S)).toEqual([]);
    expect(actionsFor('n-ask', meta(), S)).toEqual([]);
  });

  it('never offers a label PlantPal has no endpoint for on a live id', () => {
    const forbidden = [
      'Reschedule',
      'Abandon plan',
      'Dismiss this problem',
      'Mark as resolved',
      'Log a symptom',
      'Save to my notes',
      'Mark all read',
    ];
    const liveIds = [
      'n-plant-1',
      'n-garden',
      'n-garden-more',
      'n-reminders',
      'n-care',
      'n-journal',
      'n-log-1',
      'n-treatments',
      'n-treatments-more',
      'n-problems',
      'n-treatment-301',
      'n-treatment-302',
      'n-ident',
      'n-scan-1',
      'n-species',
      'n-species-1',
      'n-platform',
      'n-today',
      'n-account',
      'n-ask',
    ];
    for (const id of liveIds) {
      for (const label of forbidden) {
        expect(actionsFor(id, meta(), S)).not.toContain(label);
      }
    }
  });
});
