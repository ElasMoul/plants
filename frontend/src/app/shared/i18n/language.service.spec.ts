import { FR } from './fr';
import { DEFAULT_LANGUAGE, LanguageService, SUPPORTED_LANGUAGES, adoptProfileLanguage, pluralSuffix, readLanguage, translate } from './language.service';
import { localizedPaginator } from './localized-paginator';

describe('User interface languages', () => {
  afterEach(() => localStorage.removeItem('plantpal.language'));

  it('preserves English by default and ignores unsupported stored languages', () => {
    localStorage.removeItem('plantpal.language');
    expect(readLanguage()).toBe('en');
    localStorage.setItem('plantpal.language', 'de');
    expect(readLanguage()).toBe('en');
    expect(translate('Sign in')).toBe('Sign in');
  });

  it('restores French and declares the document language and direction', () => {
    localStorage.setItem('plantpal.language', 'fr');
    const service = new LanguageService(document);
    expect(service.locale).toBe('fr-FR');
    expect(document.documentElement.lang).toBe('fr');
    expect(document.documentElement.dir).toBe('ltr');
    expect(translate('Sign in')).toBe('Se connecter');
  });

  it('substitutes values without interpreting user text as markup or more placeholders', () => {
    localStorage.setItem('plantpal.language', 'fr');
    expect(translate('Chatting about {0}', ['Fern {1} <script>']))
      .toBe('Discussion à propos de Fern {1} <script>');
    expect(translate('Uncatalogued message')).toBe('Uncatalogued message');
  });

  it('keeps all translation placeholders aligned with their source', () => {
    const placeholders = (text: string) => [...new Set(text.match(/\{\d+\}/g))].sort();
    for (const [source, translation] of Object.entries(FR)) {
      expect({ source, placeholders: placeholders(translation) })
        .toEqual({ source, placeholders: placeholders(source) });
    }
  });

  it('localizes pagination including empty and final partial pages', () => {
    localStorage.setItem('plantpal.language', 'fr');
    const labels = localizedPaginator();
    expect(labels.nextPageLabel).toBe('Page suivante');
    expect(labels.getRangeLabel(0, 20, 0)).toBe('0–0 sur 0');
    expect(labels.getRangeLabel(1, 20, 23)).toBe('21–23 sur 23');
  });

  it('uses French plural rules and translates server health summaries without changing their values', () => {
    localStorage.setItem('plantpal.language', 'fr');
    expect([0, 1, 2].map(pluralSuffix)).toEqual(['', '', 's']);
    expect(translate('All healthy')).toBe('Toutes en bonne santé');
    expect(translate('2 issue(s)')).toBe('2 problèmes');
    localStorage.setItem('plantpal.language', 'en');
    expect(pluralSuffix(0)).toBe('s');
    expect(translate('2 issue(s)')).toBe('2 issue(s)');
  });

  it('continues in English when browser storage is unavailable', () => {
    const read = jest.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(translate('Sign in')).toBe('Sign in');
    read.mockRestore();
  });
});


describe('Language policy', () => {
  afterEach(() => { localStorage.clear(); jest.restoreAllMocks(); });

  it('defines exactly English and French, with English as the default', () => {
    expect([...SUPPORTED_LANGUAGES]).toEqual(['en', 'fr']);
    expect(DEFAULT_LANGUAGE).toBe('en');
  });

  it('shows English to a visitor with no choice whatever the browser language is', () => {
    jest.spyOn(navigator, 'languages', 'get').mockReturnValue(['fr-FR', 'fr']);
    jest.spyOn(navigator, 'language', 'get').mockReturnValue('fr-FR');
    localStorage.clear();
    expect(readLanguage()).toBe('en');
    expect(translate('Sign in')).toBe('Sign in');
    expect(new LanguageService(document).language).toBe('en');
  });

  it('shows English for a stored unsupported language such as ar', () => {
    localStorage.setItem('plantpal.language', 'ar');
    expect(readLanguage()).toBe('en');
    expect(translate('Sign in')).toBe('Sign in');
  });

  it('persists a French choice across a fresh service instance and rejects unsupported codes', () => {
    const service = new LanguageService(document);
    service.select('ar');
    expect(localStorage.getItem('plantpal.language')).toBeNull();
    localStorage.setItem('plantpal.language', 'fr');
    expect(new LanguageService(document).language).toBe('fr');
  });

  it('lets the profile language win over the device value and reports when a reload is needed', () => {
    localStorage.setItem('plantpal.language', 'en');
    document.documentElement.lang = 'en';
    expect(adoptProfileLanguage('fr')).toBe(true);
    expect(localStorage.getItem('plantpal.language')).toBe('fr');
    document.documentElement.lang = 'fr';
    expect(adoptProfileLanguage('fr')).toBe(false);
    expect(adoptProfileLanguage('ar')).toBe(true);
    expect(localStorage.getItem('plantpal.language')).toBe('en');
    expect(adoptProfileLanguage(null)).toBe(false);
  });

  it('falls back to the English text for a missing or empty French entry, never a blank or key', () => {
    localStorage.setItem('plantpal.language', 'fr');
    const original = FR['Sign in'];
    expect(translate('Message with no French entry')).toBe('Message with no French entry');
    (FR as Record<string, string>)['Sign in'] = '';
    try {
      expect(translate('Sign in')).toBe('Sign in');
    } finally { (FR as Record<string, string>)['Sign in'] = original; }
  });
});
