export interface ParsedDetail {
  intro: string | null;
  items: string[];
}

const NUMBERED_MARKER = /(?:^|\s)\d{1,2}\.\s+/g;
const BULLET_MARKER = /^[-*•]\s+/;

// AI-generated care-card detail text sometimes reads as a numbered or bulleted procedure
// ("1. Mix... 2. Apply... 3. Wait...") rather than prose. Detect that shape so the UI can
// render an actual list instead of one dense paragraph — never throws, falls back to null
// (render as plain text) for anything that doesn't clearly look like a list.
export function parseDetailAsList(detail: string | null | undefined): ParsedDetail | null {
  if (!detail) return null;

  const numbered = parseNumbered(detail);
  if (numbered) return numbered;

  return parseBulleted(detail);
}

function parseNumbered(detail: string): ParsedDetail | null {
  const matches = [...detail.matchAll(NUMBERED_MARKER)];
  if (matches.length < 2) return null;

  const firstIndex = matches[0].index ?? 0;
  const intro = detail.slice(0, firstIndex).trim() || null;

  const items: string[] = [];
  for (let i = 0; i < matches.length; i++) {
    const matchIndex = matches[i].index ?? 0;
    const start = matchIndex + matches[i][0].length;
    const end = i + 1 < matches.length ? (matches[i + 1].index ?? detail.length) : detail.length;
    const item = detail.slice(start, end).trim();
    if (item) items.push(item);
  }

  return items.length >= 2 ? { intro, items } : null;
}

function parseBulleted(detail: string): ParsedDetail | null {
  const lines = detail.split('\n').map(line => line.trim()).filter(Boolean);
  const bulletLines = lines.filter(line => BULLET_MARKER.test(line));
  if (bulletLines.length < 2) return null;

  // Tolerate one non-bulleted lead-in line ("Apply as follows:") but require every other
  // line to be a bullet — otherwise this is probably just prose with a stray dash in it.
  if (bulletLines.length < lines.length - 1) return null;

  const intro = lines.length > bulletLines.length ? lines[0] : null;
  const items = bulletLines.map(line => line.replace(BULLET_MARKER, '').trim());

  return { intro, items };
}
