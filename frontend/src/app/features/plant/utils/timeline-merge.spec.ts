import { IdentificationResponse } from '../../identification/models/identification.model';
import { JournalEvent } from '../models/journal-event.model';
import { eventTime, mergeTimeline } from './timeline-merge';

describe('mergeTimeline (PLA-111)', () => {
  const scan = (id: number, createdAt: string) => ({ id, createdAt }) as unknown as IdentificationResponse;
  const event = (id: number, eventDate: string) =>
    ({ id, plantId: 1, type: 'PRUNING', eventDate, note: null }) as JournalEvent;

  const scans = [scan(1, '2026-03-01T10:00:00'), scan(2, '2026-05-01T10:00:00')];
  const events = [event(10, '2026-04-01'), event(11, '2026-06-01'), event(12, '2026-02-01')];

  const label = (items: ReturnType<typeof mergeTimeline>) =>
    items.map(i => (i.kind === 'scan' ? `s${i.scan.id}` : `e${i.event.id}`));

  it('interleaves photos and events newest first', () => {
    expect(label(mergeTimeline(scans, events, 'desc'))).toEqual(['e11', 's2', 'e10', 's1', 'e12']);
  });

  it('interleaves oldest first in the other direction', () => {
    expect(label(mergeTimeline(scans, events, 'asc'))).toEqual(['e12', 's1', 'e10', 's2', 'e11']);
  });

  it('places a same-day scan after the start-of-day event', () => {
    const merged = mergeTimeline([scan(3, '2026-04-01T08:00:00')], [event(13, '2026-04-01')], 'asc');
    expect(label(merged)).toEqual(['e13', 's3']);
  });

  it('handles empty inputs and does not mutate them', () => {
    expect(mergeTimeline([], [])).toEqual([]);
    const copy = [...events];
    mergeTimeline(scans, events);
    expect(events).toEqual(copy);
  });

  it('parses event dates as local midnight', () => {
    expect(new Date(eventTime('2026-04-01')).getHours()).toBe(0);
    expect(new Date(eventTime('2026-04-01')).getDate()).toBe(1);
  });
});
