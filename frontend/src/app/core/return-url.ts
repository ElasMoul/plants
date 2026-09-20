/**
 * Validated returnUrl convention (ADR-5/D8): a returnUrl must resolve to a known
 * same-origin app route or it is discarded in favour of the default landing.
 * Never treated as a URL — protocol-relative ('//host/...'), absolute
 * ('https://...') and unknown-prefix values are all rejected, so this cannot
 * become an open-redirect (see platform-vault mission 9b774285's risk register).
 */
const KNOWN_TOP_LEVEL_SEGMENTS = [
  'home',
  'dashboard',
  'garden',
  'plants',
  'identify',
  'treatment',
  'treatment-plans',
  'reminders',
  'chat',
  'preferences',
  'voice-test',
];

export function sanitizeReturnUrl(candidate: string | null | undefined): string | null {
  if (!candidate) return null;
  // Must be a single-slash-rooted relative path — rejects 'https://evil.tld',
  // '//evil.tld' (protocol-relative) and bare segments alike.
  if (!candidate.startsWith('/') || candidate.startsWith('//')) return null;
  if (candidate.includes('://')) return null;

  const path = candidate.split('?')[0].split('#')[0];
  const topLevelSegment = path.split('/').filter(Boolean)[0];
  if (!topLevelSegment || !KNOWN_TOP_LEVEL_SEGMENTS.includes(topLevelSegment)) return null;

  return candidate;
}
