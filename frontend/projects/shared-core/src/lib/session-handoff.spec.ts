import { User } from './models/user.model';
import {
  buildAtlasHandoffUrl,
  consumeSessionHandoff,
  SESSION_TOKEN_KEY,
  SESSION_USER_KEY,
} from './session-handoff';

const USER: User = { id: 7, email: 'a@b.c', firstName: 'Mo', lastName: 'El', status: 'ACTIVE' };
const TOKEN_STORAGE_CONTRACT = 'plantpal_token';
const USER_STORAGE_CONTRACT = 'plantpal_user';

/** A minimal Window stand-in so the tests never touch the real address bar. */
function fakeWindow(hash: string) {
  const store = new Map<string, string>();
  const location = { hash, pathname: '/', search: '' };
  const replaceState = jest.fn((_state: unknown, _unused: string, url: string) => {
    const replacement = new URL(url, 'https://localhost:8445');
    location.hash = replacement.hash;
    location.pathname = replacement.pathname;
    location.search = replacement.search;
  });
  return {
    win: {
      location,
      history: { replaceState },
      localStorage: {
        setItem: (k: string, v: string) => void store.set(k, v),
        getItem: (k: string) => store.get(k) ?? null,
      },
    } as unknown as Window,
    store,
    replaceState,
  };
}

describe('session handoff (G — cross-origin login → atlas)', () => {
  it('[PP-AUTH-003][sessionhandoff] builds an atlas URL carrying the session in the fragment only', () => {
    const url = buildAtlasHandoffUrl('https://localhost:8445', 'JWT123', USER);
    expect(url.startsWith('https://localhost:8445/#session=')).toBe(true);
    // nothing token-shaped before the fragment (never in path or query)
    expect(url.split('#')[0]).toBe('https://localhost:8445/');
    expect(url).not.toContain('JWT123'); // encoded, not raw
  });

  it('strips trailing slashes from the base', () => {
    expect(buildAtlasHandoffUrl('http://localhost:4300/', 't', null).startsWith('http://localhost:4300/#session=')).toBe(true);
  });

  it('[PP-AUTH-003][sessionhandoff] preserves the exact cross-frontend storage-key contract', () => {
    expect(SESSION_TOKEN_KEY).toBe(TOKEN_STORAGE_CONTRACT);
    expect(SESSION_USER_KEY).toBe(USER_STORAGE_CONTRACT);

    const url = buildAtlasHandoffUrl('https://localhost:8445', 'JWT123', USER);
    const { win, store } = fakeWindow('#' + url.split('#')[1]);

    expect(consumeSessionHandoff(win)).toBe(true);
    expect(store.get(TOKEN_STORAGE_CONTRACT)).toBe('JWT123');
    expect(JSON.parse(store.get(USER_STORAGE_CONTRACT)!)).toEqual(USER);
  });

  it('[PP-AUTH-003][sessionhandoff] characterizes the bearer fragment as replayable', () => {
    const url = buildAtlasHandoffUrl('https://localhost:8445', 'JWT123', USER);
    const hash = '#' + url.split('#')[1];
    const firstAtlasOrigin = fakeWindow(hash);
    const secondAtlasOrigin = fakeWindow(hash);

    expect(consumeSessionHandoff(firstAtlasOrigin.win)).toBe(true);
    expect(consumeSessionHandoff(secondAtlasOrigin.win)).toBe(true);
    expect(firstAtlasOrigin.store.get(TOKEN_STORAGE_CONTRACT)).toBe('JWT123');
    expect(secondAtlasOrigin.store.get(TOKEN_STORAGE_CONTRACT)).toBe('JWT123');
  });

  it('[PP-AUTH-003][sessionhandoff] scrubs the fragment after one consumption', () => {
    const url = buildAtlasHandoffUrl('https://localhost:8445', 'JWT123', USER);
    const { win, replaceState, store } = fakeWindow('#' + url.split('#')[1]);

    expect(consumeSessionHandoff(win)).toBe(true);
    expect(replaceState).toHaveBeenCalledWith(null, '', '/');
    expect(win.location.hash).toBe('');
    expect(consumeSessionHandoff(win)).toBe(false);
    expect(store.size).toBe(2);
  });

  it('ignores an absent or unrelated fragment', () => {
    const none = fakeWindow('');
    expect(consumeSessionHandoff(none.win)).toBe(false);
    expect(none.replaceState).not.toHaveBeenCalled();

    const other = fakeWindow('#anchor');
    expect(consumeSessionHandoff(other.win)).toBe(false);
  });

  it('[PP-AUTH-003][sessionhandoff] scrubs but does not store a malformed payload', () => {
    const { win, store, replaceState } = fakeWindow('#session=%%%not-base64%%%');
    expect(consumeSessionHandoff(win)).toBe(false);
    expect(store.size).toBe(0);
    expect(replaceState).toHaveBeenCalled(); // never leave a token-shaped blob visible
  });

  it('survives unicode in the user payload', () => {
    const unicodeUser = { ...USER, firstName: 'Мурад', lastName: '日本語' };
    const url = buildAtlasHandoffUrl('https://localhost:8445', 'tok', unicodeUser);
    const { win, store } = fakeWindow('#' + url.split('#')[1]);
    expect(consumeSessionHandoff(win)).toBe(true);
    expect(JSON.parse(store.get(SESSION_USER_KEY)!).firstName).toBe('Мурад');
  });
});
