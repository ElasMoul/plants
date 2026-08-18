import { User } from './models/user.model';

/**
 * Cross-origin session handoff (classic app → atlas).
 *
 * The two frontends are served on different ports in the compose deploy
 * (classic :8444, atlas :8445), and localStorage is origin-scoped — so a login
 * on the classic origin is not automatically visible to atlas. When the user
 * asks to continue into the Atlas after sign-in, the classic app redirects to
 * the atlas origin carrying the fresh session in the URL FRAGMENT; atlas
 * consumes it once at boot, stores it under the same keys AuthService reads,
 * and scrubs the URL.
 *
 * A fragment (never a query string) on purpose: fragments are not sent in the
 * HTTP request, so the token stays out of server/proxy access logs, and
 * consumeSessionHandoff() removes it from the address bar and history
 * immediately after reading it.
 */

/** The storage keys — one contract for every PlantPal frontend. */
export const SESSION_TOKEN_KEY = 'plantpal_token';
export const SESSION_USER_KEY = 'plantpal_user';

const FRAGMENT_PREFIX = '#session=';

interface HandoffPayload {
  t: string;
  u: User | null;
}

/** base64url — plain btoa output is not URL-safe (+, /, =). */
function encode(payload: HandoffPayload): string {
  const json = JSON.stringify(payload);
  return btoa(unescape(encodeURIComponent(json)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function decode(raw: string): HandoffPayload | null {
  try {
    const b64 = raw.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(escape(atob(b64)));
    const parsed = JSON.parse(json) as HandoffPayload;
    return typeof parsed.t === 'string' && parsed.t.length > 0 ? parsed : null;
  } catch {
    return null;
  }
}

/** The URL the classic login redirects to when "open the Atlas" is checked. */
export function buildAtlasHandoffUrl(atlasBaseUrl: string, token: string, user: User | null): string {
  const base = atlasBaseUrl.replace(/\/+$/, '');
  return `${base}/${FRAGMENT_PREFIX}${encode({ t: token, u: user })}`;
}

/**
 * Consume a session handoff from the current URL, if one is present. Stores the
 * token + user under the shared keys and scrubs the fragment from the address
 * bar (replaceState — it never enters history). Returns true when a session was
 * consumed. Call this BEFORE bootstrapping the app, so AuthService already sees
 * the session on first read.
 */
export function consumeSessionHandoff(win: Window = window): boolean {
  const hash = win.location.hash;
  if (!hash.startsWith(FRAGMENT_PREFIX)) return false;
  const payload = decode(hash.slice(FRAGMENT_PREFIX.length));
  // A malformed fragment is still scrubbed — never leave a token-shaped blob in the bar.
  win.history.replaceState(null, '', win.location.pathname + win.location.search);
  if (!payload) return false;
  win.localStorage.setItem(SESSION_TOKEN_KEY, payload.t);
  if (payload.u) {
    win.localStorage.setItem(SESSION_USER_KEY, JSON.stringify(payload.u));
  }
  return true;
}
