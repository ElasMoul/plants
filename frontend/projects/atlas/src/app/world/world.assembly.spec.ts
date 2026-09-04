import { assembleWorld } from './world.assembly';
import { emptySources, IdentificationDto, PlantDto, SpeciesDto, WorldSources } from './world.dto';

function plant(id: number, over: Partial<PlantDto> = {}): PlantDto {
  return { id, nickname: `Plant ${id}`, species: 'Ficus lyrata', commonName: 'Fig', nextWaterDays: 5, healthStatus: 'HEALTHY', ...over };
}
function species(id: number): SpeciesDto {
  return { id, scientificName: `Genus sp${id}`, commonName: `Common ${id}` };
}
function ident(id: number, status = 'COMPLETED', over: Partial<IdentificationDto> = {}): IdentificationDto {
  return { id, species: 'Ficus lyrata', commonName: 'Fig', healthStatus: 'HEALTHY', status, createdAt: `2026-08-0${id}T10:00:00Z`, ...over };
}

function sources(over: Partial<WorldSources> = {}): WorldSources {
  return emptySources({
    now: '2026-09-03T09:12:00Z',
    plants: [plant(1), plant(2), plant(3)],
    species: [species(1), species(2)],
    identifications: [ident(1)],
    user: { firstName: 'Mo', lastName: 'El', email: 'mo@example.com' },
    ...over,
  });
}

const idsOf = (w: ReturnType<typeof assembleWorld>) => w.nodes.map(n => n.id);

describe('assembleWorld (H5 — the live round-1 spine)', () => {
  it('builds the garden hub as the initial focus with the spine nodes', () => {
    const w = assembleWorld(sources());
    expect(w.initialFocus).toBe('n-garden');
    expect(idsOf(w)).toEqual(expect.arrayContaining(['n-garden', 'n-account', 'n-platform', 'n-ident', 'n-species', 'n-reminders', 'n-care']));
  });

  it('recaps the garden from real plant data', () => {
    const w = assembleWorld(sources({ plants: [plant(1, { nextWaterDays: 0 }), plant(2)] }));
    expect(w.nodes.find(n => n.id === 'n-garden')!.recap).toBe('2 plants · 1 need water');
  });

  it('shows the signed-in user on the account node', () => {
    const acc = assembleWorld(sources()).nodes.find(n => n.id === 'n-account')!;
    expect(acc.name).toBe("Mo's account");
    expect(acc.recap).toBe('mo@example.com');
    expect(acc.body).toContain('mo@example.com');
  });

  it('escapes user-originated text in generated bodies', () => {
    const w = assembleWorld(sources({ plants: [plant(1, { nickname: '<img src=x onerror=alert(1)>' })] }));
    const p = w.nodes.find(n => n.id === 'n-plant-1')!;
    expect(p.body).not.toContain('<img');
    expect(p.body).toContain('&lt;img');
  });

  describe('identifications (the async family)', () => {
    it('marks the ident node failed when the latest scan failed', () => {
      const w = assembleWorld(sources({ identifications: [ident(2, 'FAILED'), ident(1)] }));
      const n = w.nodes.find(x => x.id === 'n-ident')!;
      expect(n.state).toBe('failed');
      expect(n.body).toContain('Try the scan again');
    });
    it('sets hasPendingScan while a scan is analysing', () => {
      expect(assembleWorld(sources({ identifications: [ident(2, 'PENDING')] })).hasPendingScan).toBe(true);
      expect(assembleWorld(sources()).hasPendingScan).toBe(false);
    });
    it('links the identify → species path', () => {
      const w = assembleWorld(sources());
      expect(w.edges).toEqual(expect.arrayContaining([['n-ident', 'n-species']]));
    });
  });

  describe('density collapse (C4)', () => {
    it('draws all plants when fewer than four', () => {
      const w = assembleWorld(sources());
      expect(w.nodes.filter(n => n.id.startsWith('n-plant-'))).toHaveLength(3);
      expect(idsOf(w).includes('n-garden-more')).toBe(false);
    });
    it('draws two + "+N more" when four or more', () => {
      const w = assembleWorld(sources({ plants: [plant(1), plant(2), plant(3), plant(4), plant(5)] }));
      expect(w.nodes.filter(n => n.id.startsWith('n-plant-'))).toHaveLength(2);
      expect(w.nodes.find(n => n.id === 'n-garden-more')!.recap).toBe('+3 more');
    });
    it('ranks issue plants first', () => {
      const many = [plant(1, { nextWaterDays: 10 }), plant(2, { healthStatus: 'ISSUES_DETECTED' }), plant(3), plant(4)];
      const w = assembleWorld(sources({ plants: many }));
      expect(idsOf(w)).toContain('n-plant-2');
    });
  });

  describe('deferred families + problems', () => {
    it('renders reminders/care as honest deferred empty panels', () => {
      const w = assembleWorld(sources());
      expect(w.nodes.find(n => n.id === 'n-reminders')!.state).toBe('empty');
      expect(w.nodes.find(n => n.id === 'n-care')!.body).toContain('state--empty');
    });
    it('adds a Problems node only when plants need attention', () => {
      expect(idsOf(assembleWorld(sources())).includes('n-problems')).toBe(false);
      const w = assembleWorld(sources({ plants: [plant(1, { healthStatus: 'ISSUES_DETECTED' })] }));
      expect(idsOf(w).includes('n-problems')).toBe(true);
    });
  });

  describe('the tour (H9): scans are nodes, rows navigate', () => {
    it('draws each identification as a node linked to its plant', () => {
      const w = assembleWorld(sources({ identifications: [
        { id: 5, species: 'Ficus lyrata', commonName: 'Fig', healthStatus: null, status: 'COMPLETED', createdAt: '2026-08-01T10:00:00Z', plantId: 1 },
      ] }));
      const scan = w.nodes.find(n => n.id === 'n-scan-5')!;
      expect(scan).toBeDefined();
      expect(scan.kindLabel).toBe('Scan');
      expect(w.edges).toEqual(expect.arrayContaining([['n-ident', 'n-scan-5'], ['n-scan-5', 'n-plant-1']]));
      expect(scan.body).toContain('data-goto="n-plant-1"');
    });
    it('garden rows doc-link to drawn plant nodes; page nodes exist', () => {
      const w = assembleWorld(sources());
      const garden = w.nodes.find(n => n.id === 'n-garden')!;
      expect(garden.body).toContain('data-goto="n-plant-1"');
      for (const id of ['n-ask', 'n-today', 'n-treatments']) {
        expect(w.nodes.some(n => n.id === id)).toBe(true);
      }
    });
  });

  describe('per-family failure (C25)', () => {
    it('renders a family failure as a failed state inside its own hub', () => {
      const w = assembleWorld(
        sources({ failures: [{ family: 'reminders', status: 503, at: '2026-09-03T09:12:00Z' }] }),
      );
      const n = w.nodes.find(x => x.id === 'n-reminders')!;
      expect(n.state).toBe('failed');
      expect(n.body).toContain('state--error');
      expect(n.body).toContain('Fetch this region');
      expect(n.body).toContain('nothing moved');
      expect(n.failure!.waysForward).toEqual(['Fetch this region']);
      // the rest of the board is untouched — degradation is per-node material
      expect(w.nodes.find(x => x.id === 'n-care')!.state).toBe('empty');
      expect(w.nodes.find(x => x.id === 'n-garden')!.state).toBeUndefined();
    });

    it('offers the dashboard a second way through', () => {
      const w = assembleWorld(
        sources({ failures: [{ family: 'dashboard', status: 500, at: '2026-09-03T09:12:00Z' }] }),
      );
      const n = w.nodes.find(x => x.id === 'n-today')!;
      expect(n.failure!.waysForward).toEqual(['Fetch this region', 'Count again']);
      expect(n.body).toContain('Count again');
    });
  });

  describe('meta — the loader facts beside the board', () => {
    it('lists every plant and every due reminder', () => {
      const w = assembleWorld(
        sources({
          plants: [plant(1), plant(2)],
          reminders: [
            { id: 601, plantId: 1, plantNickname: 'Plant 1', careType: 'WATERING', frequencyDays: 7, nextDueAt: '2026-09-01T08:00:00Z', enabled: true, recurring: true },
            { id: 602, plantId: 2, plantNickname: 'Plant 2', careType: 'FERTILIZING', frequencyDays: 30, nextDueAt: '2026-10-20T08:00:00Z', enabled: true, recurring: true },
          ],
        }),
      );
      expect(w.meta!.syncedAt).toBe('2026-09-03T09:12:00Z');
      expect(w.meta!.plantsIndex.map(p => p.id)).toEqual([1, 2]);
      expect(w.meta!.dueReminders).toEqual([
        { id: 601, plantId: 1, nextDueAt: '2026-09-01T08:00:00Z', label: 'Water · Plant 1' },
      ]);
      expect(w.meta!.hasPendingDescription).toBe(false);
    });

    it('flags a disease description still being written', () => {
      const w = assembleWorld(
        sources({
          treatments: [{ id: 301, plantId: 1, diseaseName: 'Root rot', status: 'DRAFT', descriptionStatus: 'PENDING', createdAt: '2026-09-01T09:00:00Z' }],
        }),
      );
      expect(w.meta!.hasPendingDescription).toBe(true);
      expect(w.meta!.treatmentsIndex[301].plantId).toBe(1);
    });

    it('polls for no description a course will never write', () => {
      const base = { id: 301, plantId: 1, diseaseName: 'Root rot', createdAt: '2026-09-01T09:00:00Z' };
      // no status at all is not a promise of one arriving
      expect(
        assembleWorld(sources({ treatments: [{ ...base, status: 'IN_PROGRESS' }] })).meta!
          .hasPendingDescription,
      ).toBe(false);
      // a dismissed course is finished, pending or not
      expect(
        assembleWorld(
          sources({ treatments: [{ ...base, status: 'DISMISSED', descriptionStatus: 'PENDING' }] }),
        ).meta!.hasPendingDescription,
      ).toBe(false);
    });
  });

  describe('insertion stability (C8)', () => {
    it('is identical to the centred layout when no prior cells are given', () => {
      const cells = Object.fromEntries(assembleWorld(sources()).nodes.map(n => [n.id, n.cell]));
      expect(cells['n-garden']).toEqual({ col: 0, row: 6 });
      expect(Object.fromEntries(assembleWorld(sources()).nodes.map(n => [n.id, n.cell]))).toEqual(cells);
    });

    it('keeps every prior cell and gives a new node a free one', () => {
      const before = assembleWorld(sources());
      const priorCells = Object.fromEntries(before.nodes.map(n => [n.id, n.cell]));
      const after = assembleWorld(
        sources({ plants: [plant(1), plant(2), plant(3)], species: [species(1), species(2), species(3)], priorCells }),
      );
      for (const n of before.nodes) {
        const moved = after.nodes.find(x => x.id === n.id);
        if (moved) expect(moved.cell).toEqual(n.cell);
      }
      const fresh = after.nodes.find(n => n.id === 'n-species-3')!;
      expect(priorCells[fresh.id]).toBeUndefined();
      // the free cell it took was not occupied before, in its own column
      const takenInItsColumn = before.nodes.filter(n => n.cell.col === fresh.cell.col).map(n => n.cell.row);
      expect(takenInItsColumn.length).toBeGreaterThan(0);
      expect(takenInItsColumn).not.toContain(fresh.cell.row);
    });
  });

  describe('determinism (C7)', () => {
    it('produces identical output for identical input', () => {
      expect(assembleWorld(sources())).toEqual(assembleWorld(sources()));
    });
  });
});
