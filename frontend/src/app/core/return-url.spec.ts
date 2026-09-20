import { sanitizeReturnUrl } from './return-url';

describe('sanitizeReturnUrl (D8 / open-redirect guard)', () => {
  it('accepts a known app route with a param and query string', () => {
    expect(sanitizeReturnUrl('/plants/42?tab=history')).toBe('/plants/42?tab=history');
  });

  it('accepts a known bare top-level route', () => {
    expect(sanitizeReturnUrl('/garden')).toBe('/garden');
  });

  it('rejects null and empty input', () => {
    expect(sanitizeReturnUrl(null)).toBeNull();
    expect(sanitizeReturnUrl('')).toBeNull();
  });

  it('rejects an absolute URL to another host', () => {
    expect(sanitizeReturnUrl('https://evil.example/phish')).toBeNull();
  });

  it('rejects a protocol-relative URL', () => {
    expect(sanitizeReturnUrl('//evil.example/phish')).toBeNull();
  });

  it('rejects a path that does not start with a slash', () => {
    expect(sanitizeReturnUrl('plants/42')).toBeNull();
  });

  it('rejects an unknown top-level segment', () => {
    expect(sanitizeReturnUrl('/not-a-real-route')).toBeNull();
  });

  it('rejects a javascript: pseudo-url smuggled without a leading slash check bypass', () => {
    expect(sanitizeReturnUrl('/javascript:alert(1)')).toBeNull();
  });
});
