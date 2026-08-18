import { boxesOverlap, ClearanceInput, computeTargets, PlacementNode } from './clearance';
import { buildAdjacency } from './graph';
import { Edge, Size } from './types';

const FOCUS_SIZE: Size = { w: 436, h: 300 };
const CARD_SIZE: Size = { w: 180, h: 110 };

// A focus with four direct neighbours, plus one non-neighbour off to the side.
const EDGES: Edge[] = [
  ['focus', 'n1'],
  ['focus', 'n2'],
  ['focus', 'n3'],
  ['focus', 'n4'],
];
const ORDER = ['focus', 'n1', 'n2', 'n3', 'n4', 'far'];

function makeInput(overrides: Partial<ClearanceInput> = {}): ClearanceInput {
  // All neighbours start stacked ON the focus anchor, so without clearance they
  // would all overlap it — the pass must resolve that.
  const nodes: Record<string, PlacementNode> = {
    focus: { anchor: { x: 800, y: 500 }, size: FOCUS_SIZE },
    n1: { anchor: { x: 800, y: 500 }, size: CARD_SIZE },
    n2: { anchor: { x: 810, y: 500 }, size: CARD_SIZE },
    n3: { anchor: { x: 800, y: 510 }, size: CARD_SIZE },
    n4: { anchor: { x: 790, y: 490 }, size: CARD_SIZE },
    far: { anchor: { x: 2000, y: 500 }, size: CARD_SIZE },
  };
  return { focusId: 'focus', order: ORDER, nodes, adjacency: buildAdjacency(EDGES, ORDER), ...overrides };
}

describe('clearance — the placement pass (B3)', () => {
  it('pins the focus at its own anchor (it never yields)', () => {
    const t = computeTargets(makeInput());
    expect(t['focus']).toEqual({ x: 800, y: 500 });
  });

  it('no neighbour overlaps the focus after the pass (by construction + separation)', () => {
    const input = makeInput();
    const t = computeTargets(input);
    for (const id of ['n1', 'n2', 'n3', 'n4']) {
      const overlaps = boxesOverlap(input.nodes['focus'], t['focus'], input.nodes[id], t[id]);
      expect(overlaps).toBe(false);
    }
  });

  it('leaves no overlapping pair once the pass has converged', () => {
    const input = makeInput();
    const t = computeTargets(input);
    for (let i = 0; i < ORDER.length; i++) {
      for (let j = i + 1; j < ORDER.length; j++) {
        const a = ORDER[i];
        const b = ORDER[j];
        expect(boxesOverlap(input.nodes[a], t[a], input.nodes[b], t[b])).toBe(false);
      }
    }
  });

  it('keeps each neighbour on the side its lattice angle pointed (angle preserved)', () => {
    // n2 sits to the +x side of the focus at rest; it should land on the +x side.
    const input = makeInput();
    input.nodes['n2'].anchor = { x: 1200, y: 500 }; // clearly to the right
    const t = computeTargets(input);
    expect(t['n2'].x).toBeGreaterThan(t['focus'].x);
  });

  it('is deterministic (C7): identical inputs produce identical targets', () => {
    expect(computeTargets(makeInput())).toEqual(computeTargets(makeInput()));
  });

  it('does not mutate the input node anchors', () => {
    const input = makeInput();
    computeTargets(input);
    expect(input.nodes['n1'].anchor).toEqual({ x: 800, y: 500 });
  });

  describe('arrange (drag) mode', () => {
    it('returns anchors unchanged — the lattice itself, no ring, no clearance', () => {
      const input = makeInput({ dragMode: true });
      const t = computeTargets(input);
      for (const id of ORDER) {
        expect(t[id]).toEqual(input.nodes[id].anchor);
      }
    });
  });

  describe('boxesOverlap helper', () => {
    const a: PlacementNode = { anchor: { x: 0, y: 0 }, size: { w: 100, h: 100 } };
    const b: PlacementNode = { anchor: { x: 0, y: 0 }, size: { w: 100, h: 100 } };
    it('detects overlap when centres are close', () => {
      expect(boxesOverlap(a, { x: 0, y: 0 }, b, { x: 50, y: 0 })).toBe(true);
    });
    it('reports no overlap when centres are a full box apart', () => {
      expect(boxesOverlap(a, { x: 0, y: 0 }, b, { x: 100, y: 0 })).toBe(false);
    });
  });
});
