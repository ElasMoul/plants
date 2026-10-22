export type JournalEventType = 'REPOTTING' | 'PRUNING' | 'FLOWERING' | 'FRUITING' | 'PROPAGATION';

export const JOURNAL_EVENT_TYPES: readonly JournalEventType[] = [
  'REPOTTING', 'PRUNING', 'FLOWERING', 'FRUITING', 'PROPAGATION',
];

export interface JournalEvent {
  id: number;
  plantId: number;
  type: JournalEventType;
  /** ISO local date, yyyy-MM-dd. */
  eventDate: string;
  note: string | null;
}

export interface JournalEventRequest {
  type: JournalEventType;
  eventDate: string;
  note: string | null;
}

/** Translation keys (English source text) for the event type labels. */
export const JOURNAL_EVENT_LABELS: Readonly<Record<JournalEventType, string>> = {
  REPOTTING: 'Repotting',
  PRUNING: 'Pruning',
  FLOWERING: 'Flowering',
  FRUITING: 'Fruiting',
  PROPAGATION: 'Propagation',
};
