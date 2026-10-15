import { readLanguage, setServerLanguage } from './language.service';
import { languageFromPath } from './public-routes';

describe('Public language URLs', () => {
  afterEach(() => {
    localStorage.removeItem('plantpal.language');
    setServerLanguage(null);
    window.history.pushState({}, '', '/');
  });

  it('recognises only /en and /fr as a whole first segment', () => {
    expect(languageFromPath('/fr/')).toBe('fr');
    expect(languageFromPath('/en')).toBe('en');
    expect(languageFromPath('/en?x=1')).toBe('en');
    expect(languageFromPath('/ar/')).toBeNull();
    expect(languageFromPath('/english')).toBeNull();
    expect(languageFromPath('/garden/en/')).toBeNull();
  });

  it('lets the URL language win over the stored preference on a public route', () => {
    localStorage.setItem('plantpal.language', 'en');
    window.history.pushState({}, '', '/fr/');
    expect(readLanguage()).toBe('fr');
  });

  it('keeps using the stored preference everywhere else', () => {
    localStorage.setItem('plantpal.language', 'fr');
    window.history.pushState({}, '', '/garden');
    expect(readLanguage()).toBe('fr');
  });
});
