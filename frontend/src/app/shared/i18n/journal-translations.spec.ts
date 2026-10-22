import { AR } from './ar';
import { FR } from './fr';
import { JOURNAL_EVENT_LABELS } from '../../features/plant/models/journal-event.model';

describe('plant journal translations (PLA-111)', () => {
  const keys = [
    ...Object.values(JOURNAL_EVENT_LABELS),
    'Add event', 'Edit event', 'Event type', 'Date', 'Note (optional)', 'Journal event',
    'Delete', 'Delete this event?', 'Event added.', 'Event updated.', 'Event deleted.',
    'Could not save the event.', 'Could not delete the event.', 'Could not load journal events.',
    'Cancel', 'Save', 'Edit',
  ];

  it.each(keys)('has non-empty French and Arabic text for "%s"', (key) => {
    expect(FR[key]?.trim()).toBeTruthy();
    expect(AR[key]?.trim()).toBeTruthy();
  });
});
