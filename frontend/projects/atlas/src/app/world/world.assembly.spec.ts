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

  describe('determinism (C7)', () => {
    it('produces identical output for identical input', () => {
      expect(assembleWorld(sources())).toEqual(assembleWorld(sources()));
    });
  });
});
