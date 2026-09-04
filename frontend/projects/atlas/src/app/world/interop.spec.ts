import { classicLinkFor, classicLoginLink } from './interop';

describe('interop deep-links (E2)', () => {
  const base = 'http://localhost:4200';

  it('links a plant node to its classic plant page', () => {
    expect(classicLinkFor({ id: 'n-plant-42' }, base)).toBe('http://localhost:4200/plants/42');
  });

  it('links species nodes to the garden', () => {
    expect(classicLinkFor({ id: 'n-species-7' }, base)).toBe('http://localhost:4200/garden');
    expect(classicLinkFor({ id: 'n-species' }, base)).toBe('http://localhost:4200/garden');
  });

  it('maps the fixed hubs to their classic routes', () => {
    expect(classicLinkFor({ id: 'n-reminders' }, base)).toBe('http://localhost:4200/reminders');
    expect(classicLinkFor({ id: 'n-ident' }, base)).toBe('http://localhost:4200/identify');
    expect(classicLinkFor({ id: 'n-treatment' }, base)).toBe('http://localhost:4200/treatment');
    expect(classicLinkFor({ id: 'n-garden' }, base)).toBe('http://localhost:4200/plants');
  });

  it('links the care-loop nodes to their classic pages', () => {
    expect(classicLinkFor({ id: 'n-treatment-7' }, base)).toBe('http://localhost:4200/treatment/7');
    expect(classicLinkFor({ id: 'n-treatments' }, base)).toBe('http://localhost:4200/reminders');
    expect(classicLinkFor({ id: 'n-care' }, base)).toBe('http://localhost:4200/reminders');
    expect(classicLinkFor({ id: 'n-journal' }, base)).toBe('http://localhost:4200/reminders');
    expect(classicLinkFor({ id: 'n-log-2' }, base)).toBe('http://localhost:4200/plants');
    expect(classicLinkFor({ id: 'n-scan-9' }, base)).toBe('http://localhost:4200/identify/9');
    expect(classicLinkFor({ id: 'n-ask' }, base)).toBe('http://localhost:4200/chat');
  });

  it('respects a base the reader configured', () => {
    expect(classicLinkFor({ id: 'n-treatment-7' }, 'https://plants.example.org')).toBe(
      'https://plants.example.org/treatment/7',
    );
  });

  it('returns null for nodes with no classic counterpart', () => {
    expect(classicLinkFor({ id: 'n-unknown' }, base)).toBeNull();
  });

  it('works same-origin (empty base → relative paths)', () => {
    expect(classicLinkFor({ id: 'n-plant-3' }, '')).toBe('/plants/3');
    expect(classicLoginLink('')).toBe('/login');
  });

  it('builds the login link', () => {
    expect(classicLoginLink(base)).toBe('http://localhost:4200/login');
  });
});
