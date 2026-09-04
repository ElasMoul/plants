/**
 * The atlas's date vocabulary — pure functions, no Angular, no Date.now(): every
 * "when" word is measured from the `now` the loader stamped on the sources, so the
 * assembly stays a pure function of its argument (C7).
 *
 * The voice rules bind here: a due date is spoken ("Today", "In 3 days"), never
 * rendered as machine time; nothing here emits the word "loading", a spinner or a
 * bare ellipsis.
 */

export const DAY_MS = 86_400_000;

export type DueWindow = 'server-day' | 'rolling-24h';
export type DateStyle = 'relative' | 'absolute';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function startOfDay(t: number): number {
  const d = new Date(t);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

function two(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/**
 * Whole days from `nowIso` to `iso`. Under 'server-day' the boundary is local
 * midnight (what the dashboard's buckets mean); under 'rolling-24h' it is a whole
 * elapsed day from the instant (what the classic list means). They disagree near
 * midnight — that is the point of the setting.
 */
export function daysUntil(iso: string, nowIso: string, rule: DueWindow = 'server-day'): number {
  const t = Date.parse(iso);
  const now = Date.parse(nowIso);
  if (!Number.isFinite(t) || !Number.isFinite(now)) return 0;
  if (rule === 'rolling-24h') {
    const whole = Math.trunc((t - now) / DAY_MS);
    return whole === 0 ? 0 : whole; // never hand back a negative zero
  }
  return Math.round((startOfDay(t) - startOfDay(now)) / DAY_MS);
}

/** Due means due now or already past — never "due soon". */
export function isDue(iso: string, nowIso: string, rule: DueWindow = 'server-day'): boolean {
  return daysUntil(iso, nowIso, rule) <= 0;
}

/** "Jul 24" — the short form used wherever a relative word would lie. */
export function dateLabel(iso: string): string {
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return '—';
  const d = new Date(t);
  return `${MONTHS[d.getMonth()]} ${two(d.getDate())}`;
}

/** "09:12" — local wall time, 24-hour, no seconds. */
export function timeLabel(iso: string): string {
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return '—';
  const d = new Date(t);
  return `${two(d.getHours())}:${two(d.getMinutes())}`;
}

function hasHour(iso: string): boolean {
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return false;
  const d = new Date(t);
  return d.getHours() !== 0 || d.getMinutes() !== 0;
}

/**
 * The one sentence a due row says about itself. `daysOverdue` is the server's own
 * count from /dashboard: under 'server-day' it wins, because it was computed in the
 * clock that made the bucket.
 */
export function dueLine(
  iso: string,
  nowIso: string,
  settings: { dueWindow: DueWindow; dateStyle: DateStyle },
  daysOverdue?: number,
): string {
  if (settings.dateStyle === 'absolute') {
    const overdue =
      (daysOverdue != null && daysOverdue > 0) || daysUntil(iso, nowIso, settings.dueWindow) < 0;
    return overdue ? `${dateLabel(iso)} · overdue` : dateLabel(iso);
  }
  const d =
    settings.dueWindow === 'server-day' && daysOverdue != null && daysOverdue > 0
      ? -daysOverdue
      : daysUntil(iso, nowIso, settings.dueWindow);
  if (d < 0) return `Overdue ${-d} ${-d === 1 ? 'day' : 'days'}`;
  if (d === 0) return hasHour(iso) ? `Today, ${timeLabel(iso)}` : 'Today';
  if (d === 1) return 'Tomorrow';
  if (d <= 6) return `In ${d} days`;
  return dateLabel(iso);
}

/** What a journal entry says about when it happened. */
export function agoLabel(iso: string, nowIso: string, dateStyle: DateStyle = 'relative'): string {
  if (dateStyle === 'absolute') return dateLabel(iso);
  const d = daysUntil(iso, nowIso, 'server-day');
  if (d === 0) return 'Today';
  if (d === -1) return 'Yesterday';
  if (d < 0 && d >= -6) return `${-d} days ago`;
  return dateLabel(iso);
}

const CARE_LABELS: Record<string, string> = {
  WATERING: 'Water',
  LIGHT: 'Light',
  HUMIDITY: 'Humidity',
  TEMPERATURE: 'Temperature',
  FERTILIZING: 'Feed',
  REPOTTING: 'Repot',
  PRUNING: 'Prune',
  PEST: 'Pest check',
  SEASONAL: 'Season',
  BEGINNER_TIP: 'Tip',
};

/** The gardener's word for a CareType — never the enum. */
export function careLabel(careType: string): string {
  return CARE_LABELS[careType] ?? 'Care';
}

const WORDS = [
  'Zero',
  'One',
  'Two',
  'Three',
  'Four',
  'Five',
  'Six',
  'Seven',
  'Eight',
  'Nine',
  'Ten',
];

/** Small counts are written, larger ones are counted. */
export function wordNumber(n: number): string {
  return Number.isInteger(n) && n >= 0 && n <= 10 ? WORDS[n] : String(n);
}

/**
 * True while the local wall clock sits inside the quiet window. The window may wrap
 * past midnight ("21:00-07:30"), which is the usual case.
 */
export function withinQuietHours(nowIso: string, setting: string): boolean {
  if (!setting || setting === 'off') return false;
  const m = /^(\d{2}):(\d{2})-(\d{2}):(\d{2})$/.exec(setting);
  const t = Date.parse(nowIso);
  if (!m || !Number.isFinite(t)) return false;
  const d = new Date(t);
  const mins = d.getHours() * 60 + d.getMinutes();
  const from = Number(m[1]) * 60 + Number(m[2]);
  const to = Number(m[3]) * 60 + Number(m[4]);
  return from <= to ? mins >= from && mins < to : mins >= from || mins < to;
}
