import {
  anchorPosition,
  CLEARANCE,
  DEFAULT_LATTICE,
  distance,
  homePosition,
  NAV_MS,
  parseCell,
} from './geometry';
import { Cell, LatticeNode } from './types';

describe('geometry — the lattice (B1)', () => {
  describe('homePosition', () => {
    it('computes origin + cell × pitch', () => {
      // cell (2,5): x = 200 + 2*300 = 800; y = 80 + 5*180 = 980
      expect(homePosition({ col: 2, row: 5 })).toEqual({ x: 800, y: 980 });
    });

    it('places the origin cell at the configured origin', () => {
      expect(homePosition({ col: 0, row: 0 })).toEqual({ x: 200, y: 80 });
    });

    it('honours a custom (re-pitched) lattice while keeping the cell', () => {
      const tight = { originX: 200, originY: 80, pitchX: 150, pitchY: 90 };
      expect(homePosition({ col: 2, row: 5 }, tight)).toEqual({ x: 500, y: 530 });
    });
  });

  describe('determinism (C7): position is a pure function of the cell', () => {
    it('returns an identical point when called twice for the same cell', () => {
      const cell: Cell = { col: 3, row: 7 };
      expect(homePosition(cell)).toEqual(homePosition(cell));
    });

    it('does not mutate its inputs', () => {
      const cell: Cell = { col: 1, row: 1 };
      const frozen = Object.freeze({ ...cell });
      expect(() => homePosition(frozen)).not.toThrow();
      expect(frozen).toEqual({ col: 1, row: 1 });
    });
  });

  describe('insertion invariance (C8): a new node moves nothing', () => {
    it('leaves every existing node’s position unchanged when a node is added', () => {
      const before: LatticeNode[] = [
        { id: 'a', cell: { col: 0, row: 0 } },
        { id: 'b', cell: { col: 1, row: 2 } },
        { id: 'c', cell: { col: 4, row: 1 } },
      ];
      const posOf = (nodes: LatticeNode[]) =>
        nodes.map(n => ({ id: n.id, ...anchorPosition(n) }));

      const positionsBefore = posOf(before);
      const after = [...before, { id: 'd', cell: { col: 9, row: 9 } }];
      const positionsAfter = posOf(after).filter(p => p.id !== 'd');

      expect(positionsAfter).toEqual(positionsBefore);
    });
  });

  describe('anchorPosition', () => {
    it('equals home when there is no offset', () => {
      expect(anchorPosition({ cell: { col: 2, row: 2 } })).toEqual(homePosition({ col: 2, row: 2 }));
    });

    it('adds the persisted drag offset to home', () => {
      const pos = anchorPosition({ cell: { col: 1, row: 1 }, offset: { x: 25, y: -15 } });
      expect(pos).toEqual({ x: 500 + 25, y: 260 - 15 });
    });
  });

  describe('parseCell', () => {
    it('parses the "col,row" data-cell format', () => {
      expect(parseCell('2,5')).toEqual({ col: 2, row: 5 });
    });
    it('tolerates surrounding whitespace', () => {
      expect(parseCell(' 3 , 4 ')).toEqual({ col: 3, row: 4 });
    });
  });

  describe('helpers + constants', () => {
    it('distance is Euclidean', () => {
      expect(distance({ x: 0, y: 0 }, { x: 3, y: 4 })).toBe(5);
    });
    it('pins the constitution constants to the prototype values', () => {
      expect(NAV_MS).toBe(300);
      expect(CLEARANCE).toBe(34);
      expect(DEFAULT_LATTICE).toEqual({ originX: 200, originY: 80, pitchX: 300, pitchY: 180 });
    });
  });
});
