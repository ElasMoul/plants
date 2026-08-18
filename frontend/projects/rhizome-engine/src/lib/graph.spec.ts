import { buildAdjacency, neighboursOf, rank, rankName, rankNameFor, shortestPath } from './graph';
import { Edge } from './types';

// A small fixture graph:
//   focus — near1 — far1 — fringe1
//   focus — near2
//   isolated (no edges)
const EDGES: Edge[] = [
  ['focus', 'near1'],
  ['focus', 'near2'],
  ['near1', 'far1'],
  ['far1', 'fringe1'],
];
const IDS = ['focus', 'near1', 'near2', 'far1', 'fringe1', 'isolated'];

describe('graph — rank as distance (B2)', () => {
  describe('buildAdjacency', () => {
    it('is undirected (both directions present)', () => {
      const adj = buildAdjacency(EDGES, IDS);
      expect(adj['focus']).toContain('near1');
      expect(adj['near1']).toContain('focus');
    });
    it('seeds isolated nodes with an empty neighbour list', () => {
      const adj = buildAdjacency(EDGES, IDS);
      expect(adj['isolated']).toEqual([]);
    });
    it('preserves edge insertion order (deterministic)', () => {
      const adj = buildAdjacency(EDGES, IDS);
      expect(adj['focus']).toEqual(['near1', 'near2']);
    });
  });

  describe('rank (BFS distance)', () => {
    const adj = buildAdjacency(EDGES, IDS);
    it('assigns the focus distance 0', () => {
      expect(rank('focus', adj)['focus']).toBe(0);
    });
    it('computes correct distances outward', () => {
      const d = rank('focus', adj);
      expect(d).toMatchObject({ focus: 0, near1: 1, near2: 1, far1: 2, fringe1: 3 });
    });
    it('omits unreachable nodes', () => {
      expect(rank('focus', adj)['isolated']).toBeUndefined();
    });
    it('is recomputed per focus (C3): a different focus yields different ranks', () => {
      const fromFar = rank('far1', adj);
      expect(fromFar).toMatchObject({ far1: 0, near1: 1, fringe1: 1, focus: 2, near2: 3 });
    });
  });

  describe('rankName mapping (C12)', () => {
    it('maps 0/1/2/≥3 to focus/near/far/fringe', () => {
      expect(rankName(0)).toBe('focus');
      expect(rankName(1)).toBe('near');
      expect(rankName(2)).toBe('far');
      expect(rankName(3)).toBe('fringe');
      expect(rankName(7)).toBe('fringe');
    });
  });

  describe('rankNameFor', () => {
    const d = rank('focus', buildAdjacency(EDGES, IDS));
    it('names reachable nodes by distance', () => {
      expect(rankNameFor('near2', d)).toBe('near');
      expect(rankNameFor('far1', d)).toBe('far');
    });
    it('treats an unreachable node as the fringe', () => {
      expect(rankNameFor('isolated', d)).toBe('fringe');
    });
  });

  describe('shortestPath', () => {
    const adj = buildAdjacency(EDGES, IDS);
    it('returns a single-node path from a node to itself', () => {
      expect(shortestPath('focus', 'focus', adj)).toEqual(['focus']);
    });
    it('returns the chain inclusive of both ends', () => {
      expect(shortestPath('focus', 'fringe1', adj)).toEqual(['focus', 'near1', 'far1', 'fringe1']);
    });
    it('returns [] when the target is unreachable (nothing faked)', () => {
      expect(shortestPath('focus', 'isolated', adj)).toEqual([]);
    });
  });

  describe('neighboursOf', () => {
    it('returns the focus’s direct neighbours (rank 1)', () => {
      const adj = buildAdjacency(EDGES, IDS);
      expect(neighboursOf('focus', adj)).toEqual(['near1', 'near2']);
    });
    it('returns a copy (mutating it does not corrupt the adjacency)', () => {
      const adj = buildAdjacency(EDGES, IDS);
      neighboursOf('focus', adj).push('mutant');
      expect(adj['focus']).toEqual(['near1', 'near2']);
    });
  });
});
