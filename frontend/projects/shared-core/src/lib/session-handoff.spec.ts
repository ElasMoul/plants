import { User } from './models/user.model';
import {
  buildAtlasHandoffUrl,
  consumeSessionHandoff,
  SESSION_TOKEN_KEY,
  SESSION_USER_KEY,
} from './session-handoff';

const USER: User = { id: 7, email: 'a@b.c', firstName: 'Mo', lastName: 'El', status: 'ACTIVE' };

/** A minimal Window stand-in so the tests never touch the real address bar. */
function fakeWindow(hash: string) {
  const store = new Map<string, string>();
  const replaceState = jest.fn();
  return {
    win: {
      location: { hash, pathname: '/', search: '' },
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
  it('builds an atlas URL carrying the session in the fragment only', () => {
    const url = buildAtlasHandoffUrl('https://localhost:8445', 'JWT123', USER);
    expect(url.startsWith('https://localhost:8445/#session=')).toBe(true);
    // nothing token-shaped before the fragment (never in path or query)
    expect(url.split('#')[0]).toBe('https://localhost:8445/');
    expect(url).not.toContain('JWT123'); // encoded, not raw
  });

  it('strips trailing slashes from the base', () => {
    expect(buildAtlasHandoffUrl('http://localhost:4300/', 't', null).startsWith('http://localhost:4300/#session=')).toBe(true);
  });

  it('round-trips: consume stores the token + user under the shared keys', () => {
    const url = buildAtlasHandoffUrl('https://localhost:8445', 'JWT123', USER);
    const { win, store } = fakeWindow('#' + url.split('#')[1]);

    expect(consumeSessionHandoff(win)).toBe(true);
    expect(store.get(SESSION_TOKEN_KEY)).toBe('JWT123');
    expect(JSON.parse(store.get(SESSION_USER_KEY)!)).toEqual(USER);
  });

  it('scrubs the fragment from the URL after consuming', () => {
    const url = buildAtlasHandoffUrl('https://localhost:8445', 'JWT123', USER);
    const { win, replaceState } = fakeWindow('#' + url.split('#')[1]);
    consumeSessionHandoff(win);
    expect(replaceState).toHaveBeenCalledWith(null, '', '/');
  });

  it('ignores an absent or unrelated fragment', () => {
    const none = fakeWindow('');
    expect(consumeSessionHandoff(none.win)).toBe(false);
    expect(none.replaceState).not.toHaveBeenCalled();

    const other = fakeWindow('#anchor');
    expect(consumeSessionHandoff(other.win)).toBe(false);
  });

  it('scrubs but does not store a malformed payload', () => {
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
