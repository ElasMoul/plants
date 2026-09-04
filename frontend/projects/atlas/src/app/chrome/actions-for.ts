import type { AtlasSettings } from '../settings/settings.model';
import type { WorldMeta } from '../world/world.model';

/** The per-focus mutation-rail entries (theme-a ACTIONS, verbatim). */
export const FIXTURE_ACTIONS: Record<string, string[]> = {
  'n-fig': ['Water plant', 'Fertilize', 'Add note', 'Log photo', 'Scan leaf (AI)'],
  'n-garden': ['Add new plant', 'Water all', 'Fertilize schedule', 'Add note'],
  'n-species': ['Add a species by hand', 'Import a list'],
  'n-species-more': ['Add a species by hand'],
  'n-monstera': ['Water plant', 'Add note'],
  'n-problems': ['Log a symptom', 'Start a treatment plan'],
  'n-underwater': ['Start a treatment plan', 'Dismiss this problem'],
  'n-overwater': ['Mark as resolved', 'Add note'],
  'n-rootrot': ['Start a treatment plan', 'Add note'],
  'n-treatment': ['Mark step done', 'Reschedule', 'Abandon plan'],
  'n-journal': ['Add note', 'Log photo'],
  'n-j1': ['Add note'],
  'n-j2': ['Add note'],
  'n-journal-more': ['Add note'],
  'n-reminders': ['Add a reminder', 'Snooze all'],
  'n-ident': ['Try the scan again', 'Identify by hand'],
  'n-care': ['Save to my notes'],
  'n-platform': ['Check health again'],
  'n-garden-more': ['Add new plant'],
  'n-unknown': ['Fetch this region'],
  'n-office': ['Water plant', 'Add note'],
  'n-studio': ['Water plant', 'Add note'],
};

/**
 * The Actions rail for the focused node.
 *
 * The fixture board (no meta) keeps the pinned prototype's map verbatim. A LIVE
 * board is keyed by the live id families instead, and offers only labels that
 * reach a real endpoint or an honest device-local behaviour: Reschedule, Abandon
 * plan, Dismiss this problem, Mark as resolved, Log a symptom, Save to my notes
 * and Mark all read have no server counterpart and are never offered on a live id.
 * Add note needs a plant to write onto, so it is offered only on a plant id.
 */
export function actionsFor(
  focusId: string,
  meta: WorldMeta | undefined,
  settings: AtlasSettings,
): string[] {
  if (!meta) return FIXTURE_ACTIONS[focusId] ?? [];

  const plant = /^n-plant-(\d+)$/.exec(focusId);
  if (plant) {
    const id = Number(plant[1]);
    const waters = meta.reminders.some(
      r => r.enabled && r.recurring && r.plantId === id && r.careType === 'WATERING',
    );
    return [
      waters ? 'Water plant' : 'Set a watering schedule',
      'Fertilize',
      'Add note',
      'Log a watering',
      'Scan leaf (AI)',
    ];
  }

  const treatment = /^n-treatment-(\d+)$/.exec(focusId);
  if (treatment) {
    const t = meta.treatmentsIndex[Number(treatment[1])];
    if (!t) return [];
    if (t.status === 'DRAFT') return ['Craft the treatment plan'];
    if (t.status !== 'IN_PROGRESS') return [];
    const pause =
      settings.treatment.pause === 'local'
        ? [t.paused ? 'Resume this course' : 'Pause this course']
        : [];
    return ['Mark today done', ...pause, 'Finish this course'];
  }

  if (/^n-log-/.test(focusId)) return ['Log a watering'];
  if (/^n-scan-/.test(focusId)) return ['Try the scan again'];
  if (/^n-species-\d+$/.test(focusId)) return ['Add a species by hand'];

  switch (focusId) {
    case 'n-garden':
      return ['Add new plant', 'Water all'];
    case 'n-garden-more':
      return ['Add new plant'];
    case 'n-reminders':
      return ['Add a reminder', ...(settings.reminders.snooze === 'local' ? ['Snooze all'] : [])];
    case 'n-care':
      return ['Log a watering', 'Add a reminder'];
    case 'n-journal':
    case 'n-journal-more':
      return ['Log a watering'];
    case 'n-treatments':
    case 'n-treatments-more':
    case 'n-problems':
      return ['Start a treatment plan'];
    case 'n-ident':
      return ['Try the scan again', 'Identify by hand'];
    case 'n-species':
    case 'n-species-more':
      return ['Add a species by hand'];
    case 'n-platform':
      return ['Check health again'];
    case 'n-today':
    case 'n-account':
    case 'n-ask':
      return [];
    default:
      // a live id we do not know offers nothing rather than inheriting a fixture label
      return [];
  }
}
