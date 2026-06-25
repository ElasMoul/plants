import { describe, it, expect } from 'vitest';
import { healthBadgeClass } from './health-badge.util';

describe('healthBadgeClass()', () => {
  it('returns badge-healthy for HEALTHY', () => {
    expect(healthBadgeClass('HEALTHY')).toBe('badge-healthy');
  });

  it('returns badge-issues for ISSUES_DETECTED', () => {
    expect(healthBadgeClass('ISSUES_DETECTED')).toBe('badge-issues');
  });

  it('returns badge-unknown for UNKNOWN', () => {
    expect(healthBadgeClass('UNKNOWN')).toBe('badge-unknown');
  });

  it('returns empty string for null', () => {
    expect(healthBadgeClass(null)).toBe('');
  });

  it('returns empty string for undefined', () => {
    expect(healthBadgeClass(undefined)).toBe('');
  });
});
