import { IdentificationResponse } from '../../identification/models/identification.model';
import { JournalEvent } from '../models/journal-event.model';

export type TimelineItem =
  | { kind: 'scan'; time: number; scan: IdentificationResponse }
  | { kind: 'event'; time: number; event: JournalEvent };

/** Local start of day for a yyyy-MM-dd date, so events compare with scan instants in the viewer timezone. */
export function eventTime(isoDate: string): number {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d).getTime();
}

/** Merges scans (by createdAt instant) and journal events (by date) into one list sorted by time. */
export function mergeTimeline(
  scans: readonly IdentificationResponse[],
  events: readonly JournalEvent[],
  direction: 'desc' | 'asc' = 'desc',
): TimelineItem[] {
  const items: TimelineItem[] = [
    ...scans.map((scan): TimelineItem => ({ kind: 'scan', time: new Date(scan.createdAt).getTime(), scan })),
    ...events.map((event): TimelineItem => ({ kind: 'event', time: eventTime(event.eventDate), event })),
  ];
  const sign = direction === 'desc' ? -1 : 1;
  return items.sort((a, b) => sign * (a.time - b.time));
}
