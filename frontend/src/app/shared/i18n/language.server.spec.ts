/**
 * @jest-environment node
 */
import { readLanguage, setServerLanguage } from './language.service';

describe('Language during prerender (no window, no storage)', () => {
  afterEach(() => setServerLanguage(null));

  it('defaults to English and never touches a browser API', () => {
    expect(typeof window).toBe('undefined');
    expect(readLanguage()).toBe('en');
  });

  it('uses the language pinned from the URL being rendered', () => {
    setServerLanguage('fr');
    expect(readLanguage()).toBe('fr');
  });
});
