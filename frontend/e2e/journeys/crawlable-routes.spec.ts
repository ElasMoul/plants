import { expect, request as playwrightRequest, test } from '@playwright/test';

/**
 * PLA-73: what a crawler sees. Every assertion reads the FIRST response only (Playwright's
 * APIRequestContext runs no page JavaScript). It needs the built app behind the real host rules
 * (the nginx container), so it only runs when HOST_RULES_BASE_URL points at one, e.g.
 *   HOST_RULES_BASE_URL=http://localhost:18099 HOST_RULES_ALIAS=www.planotell.platform.localhost \
 *   HOST_RULES_PREFERRED=planotell.platform.localhost npx playwright test crawlable-routes
 */
const baseURL = process.env['HOST_RULES_BASE_URL'];
const alias = process.env['HOST_RULES_ALIAS'];
const preferred = process.env['HOST_RULES_PREFERRED'];

test.describe('crawlable public routes and URL handling', () => {
  test.skip(!baseURL, 'HOST_RULES_BASE_URL is not set (needs the built app behind the nginx host rules)');

  const get = async (path: string, headers: Record<string, string> = {}) => {
    const context = await playwrightRequest.newContext({ baseURL, maxRedirects: 0 });
    try {
      const response = await context.get(path, { headers });
      return { response, body: await response.text() };
    } finally {
      await context.dispose();
    }
  };

  const landing = [
    ['/en/', 'en', 'Plant care that actually knows your plants', 'How it works'],
    ['/fr/', 'fr', 'Des soins qui connaissent vraiment vos plantes', 'Comment ça marche'],
  ] as const;

  for (const [path, lang, headline, section] of landing) {
    test(`${path} already contains the landing copy in ${lang}`, async () => {
      const { response, body } = await get(path);
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('text/html');
      expect(body).toContain(`<html lang="${lang}"`);
      expect(body).toContain(headline);
      expect(body).toContain(section);
      expect(body).not.toContain('type="password"');
    });
  }

  test('/en and /fr redirect to the trailing-slash form', async () => {
    for (const lang of ['en', 'fr']) {
      const { response } = await get(`/${lang}`);
      expect(response.status()).toBe(301);
      expect(response.headers()['location']).toBe(`/${lang}/`);
    }
  });

  for (const path of ['/en/does-not-exist', '/fr/x/y', '/totally-unknown', '/garden-not/1']) {
    test(`unknown ${path} is a real 404 with the static page`, async () => {
      const { response, body } = await get(path);
      expect(response.status()).toBe(404);
      expect(body).not.toContain('<app-root');
      expect(body).toContain('404');
    });
  }

  for (const path of ['/robots.txt', '/sitemap.xml']) {
    test(`${path} is never the shell or the login page`, async () => {
      const { response, body } = await get(path);
      // 404 until PLA-3 ships the files, then the file itself (never HTML).
      if (response.status() === 200) {
        expect(response.headers()['content-type']).not.toContain('text/html');
      } else {
        expect(response.status()).toBe(404);
      }
      expect(body).not.toContain('<app-root');
      expect(body).not.toContain('type="password"');
      expect(body).not.toContain('<script');
    });
  }

  for (const path of ['/', '/garden', '/garden/species/3', '/plants/12', '/preferences', '/login', '/treatment-plans/4']) {
    test(`authenticated or client route ${path} still gets the app shell`, async () => {
      const { response, body } = await get(path);
      expect(response.status()).toBe(200);
      expect(body).toContain('<app-root');
    });
  }

  test.describe('preferred host', () => {
    test.skip(!alias || !preferred, 'HOST_RULES_ALIAS / HOST_RULES_PREFERRED are not set');

    test('the alias redirects to the preferred host keeping path and query', async () => {
      const { response } = await get('/en/?a=1', { Host: alias! });
      expect(response.status()).toBe(301);
      expect(response.headers()['location']).toBe(`http://${preferred}/en/?a=1`);
    });

    test('the preferred host and localhost are not redirected', async () => {
      expect((await get('/en/', { Host: preferred! })).response.status()).toBe(200);
      expect((await get('/en/', { Host: 'localhost' })).response.status()).toBe(200);
    });
  });
});
