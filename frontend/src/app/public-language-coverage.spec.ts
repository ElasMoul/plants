import { AppComponent } from './app.component';
import { LanguageService } from './shared/i18n/language.service';

describe('Public language URL handling', () => {
  afterEach(() => { localStorage.removeItem('plantpal.language'); history.pushState({}, '', '/'); });

  it('moves a public language URL to the matching prefix when the language changes', () => {
    history.pushState({}, '', '/en/');
    const service = new LanguageService(document);
    const err = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => service.select('fr')).not.toThrow();
    expect(localStorage.getItem('plantpal.language')).toBe('fr');
    service.select('ar');
    err.mockRestore();
    expect(localStorage.getItem('plantpal.language')).toBe('fr');
  });

  it('recognises the landing page at the root and under the language prefixes', () => {
    const at = (url: string) => (Object.getOwnPropertyDescriptor(AppComponent.prototype, 'isLandingPage')!
      .get as () => boolean).call({ router: { url } });
    expect(at('/')).toBe(true);
    expect(at('/en/')).toBe(true);
    expect(at('/fr')).toBe(true);
    expect(at('/garden')).toBe(false);
  });

  it('does no browser work when initialised on the server', () => {
    const auth = { isLoggedIn: jest.fn() };
    const monitor = { start: jest.fn() };
    new AppComponent(auth as never, {} as never, monitor as never, {} as never, {} as never,
      {} as never, {} as never, 'server' as never).ngOnInit();
    expect(auth.isLoggedIn).not.toHaveBeenCalled();
    expect(monitor.start).not.toHaveBeenCalled();
  });
});
