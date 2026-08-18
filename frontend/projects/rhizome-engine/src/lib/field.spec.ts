import {
  advanceField,
  cardDrift,
  createRng,
  DEFAULT_FIELD_SEED,
  DRIFT_AMPLITUDE,
  driftPhase,
  linkAlpha,
  moteCount,
  seedField,
} from './field';

describe('field — deterministic decoration (B5)', () => {
  describe('createRng', () => {
    it('is deterministic for a given seed', () => {
      const a = createRng(42);
      const b = createRng(42);
      const seqA = [a(), a(), a(), a()];
      const seqB = [b(), b(), b(), b()];
      expect(seqA).toEqual(seqB);
    });
    it('produces values in [0,1)', () => {
      const r = createRng(DEFAULT_FIELD_SEED);
      for (let i = 0; i < 100; i++) {
        const v = r();
        expect(v).toBeGreaterThanOrEqual(0);
        expect(v).toBeLessThan(1);
      }
    });
  });

  describe('moteCount', () => {
    it('follows w·h/11000', () => {
      expect(moteCount(1100, 100)).toBe(10);
    });
    it('caps at 190', () => {
      expect(moteCount(4000, 3000)).toBe(190);
    });
  });

  describe('seedField', () => {
    it('is deterministic — no flicker on reload (same size + seed → identical field)', () => {
      expect(seedField(1280, 800)).toEqual(seedField(1280, 800));
    });
    it('varies with the seed', () => {
      expect(seedField(1280, 800, 1)).not.toEqual(seedField(1280, 800, 2));
    });
    it('places every mote within the field bounds', () => {
      const motes = seedField(1280, 800);
      for (const m of motes) {
        expect(m.x).toBeGreaterThanOrEqual(0);
        expect(m.x).toBeLessThanOrEqual(1280);
        expect(m.y).toBeGreaterThanOrEqual(0);
        expect(m.y).toBeLessThanOrEqual(800);
      }
    });
  });

  describe('advanceField', () => {
    it('wraps a mote past the right edge back into the field', () => {
      const motes = [{ x: 1279.95, y: 10, vx: 0.14, vy: 0, r: 1 }];
      advanceField(motes, 1280, 800);
      expect(motes[0].x).toBeCloseTo(1279.95 + 0.14 - 1280, 5);
      expect(motes[0].x).toBeGreaterThanOrEqual(0);
    });
    it('is deterministic across identical steps', () => {
      const a = seedField(600, 400);
      const b = seedField(600, 400);
      advanceField(a, 600, 400);
      advanceField(b, 600, 400);
      expect(a).toEqual(b);
    });
  });

  describe('linkAlpha', () => {
    it('is 0 beyond the cutoff', () => {
      expect(linkAlpha(15000)).toBe(0);
      expect(linkAlpha(20000)).toBe(0);
    });
    it('is 1 at zero distance and fades linearly', () => {
      expect(linkAlpha(0)).toBe(1);
      expect(linkAlpha(7500)).toBeCloseTo(0.5, 5);
    });
  });

  describe('driftPhase', () => {
    it('is within [0, 2π)', () => {
      for (let i = 0; i < 50; i++) {
        const p = driftPhase(i);
        expect(p).toBeGreaterThanOrEqual(0);
        expect(p).toBeLessThan(Math.PI * 2);
      }
    });
  });

  describe('cardDrift', () => {
    it('is deterministic in (phase, time)', () => {
      expect(cardDrift(1.2, 5000)).toEqual(cardDrift(1.2, 5000));
    });
    it('stays within a few px — "almost invisible"', () => {
      const maxX = DRIFT_AMPLITUDE * (1 + 0.45);
      const maxY = DRIFT_AMPLITUDE * (1 + 0.4);
      for (let t = 0; t < 40000; t += 250) {
        for (let i = 0; i < 8; i++) {
          const { jx, jy } = cardDrift(driftPhase(i), t);
          expect(Math.abs(jx)).toBeLessThanOrEqual(maxX + 1e-9);
          expect(Math.abs(jy)).toBeLessThanOrEqual(maxY + 1e-9);
        }
      }
    });
  });
});
