import { assembleWorld } from './world.assembly';
import { PlantDto, SpeciesDto, WorldSources } from './world.dto';

function plant(id: number, over: Partial<PlantDto> = {}): PlantDto {
  return { id, nickname: `Plant ${id}`, species: 'Ficus', commonName: 'Fig', nextWaterDays: 5, ...over };
}
function species(id: number): SpeciesDto {
  return { id, scientificName: `Genus sp${id}`, commonName: `Common ${id}` };
}

function sources(over: Partial<WorldSources> = {}): WorldSources {
  return {
    dashboard: {
      healthSummary: { totalPlants: 3, healthyCount: 2, issuesCount: 1, unknownCount: 0 },
      overdueReminders: [{ reminderId: 1, plantId: 1, plantNickname: 'A', careType: 'WATERING', nextDueAt: '', daysOverdue: 2 }],
      todayReminders: [],
      speciesCount: 2,
    },
    plants: [plant(1), plant(2), plant(3)],
    species: [species(1), species(2)],
    ...over,
  };
}

const idsOf = (w: ReturnType<typeof assembleWorld>) => w.nodes.map(n => n.id);

describe('assembleWorld (D1 — live graph assembly)', () => {
  it('builds the garden hub as the initial focus', () => {
    const w = assembleWorld(sources());
    expect(w.initialFocus).toBe('n-garden');
    expect(idsOf(w)).toEqual(expect.arrayContaining(['n-garden', 'n-account', 'n-today', 'n-species', 'n-reminders']));
  });

  it('recaps the garden from the health summary', () => {
    const w = assembleWorld(sources());
    const garden = w.nodes.find(n => n.id === 'n-garden')!;
    expect(garden.recap).toContain('3 plants');
  });

  it('adds a Problems node only when there are issues', () => {
    expect(idsOf(assembleWorld(sources())).includes('n-problems')).toBe(true);
    const healthy = sources({
      dashboard: { ...sources().dashboard, healthSummary: { totalPlants: 3, healthyCount: 3, issuesCount: 0, unknownCount: 0 } },
    });
    expect(idsOf(assembleWorld(healthy)).includes('n-problems')).toBe(false);
  });

  describe('density collapse (C4)', () => {
    it('draws all plants when there are fewer than four', () => {
      const w = assembleWorld(sources({ plants: [plant(1), plant(2), plant(3)] }));
      const plantNodes = w.nodes.filter(n => n.id.startsWith('n-plant-'));
      expect(plantNodes).toHaveLength(3);
      expect(idsOf(w).includes('n-garden-more')).toBe(false);
    });

    it('draws two + a "+N more" aggregate when four or more', () => {
      const many = [plant(1), plant(2), plant(3), plant(4), plant(5)];
      const w = assembleWorld(sources({ plants: many }));
      expect(w.nodes.filter(n => n.id.startsWith('n-plant-'))).toHaveLength(2);
      const agg = w.nodes.find(n => n.id === 'n-garden-more')!;
      expect(agg.recap).toBe('+3 more');
    });

    it('draws the two most-owed plants (issues first)', () => {
      const many = [
        plant(1, { nextWaterDays: 10 }),
        plant(2, { healthStatus: 'ISSUES_DETECTED' }),
        plant(3, { nextWaterDays: 0 }),
        plant(4, { nextWaterDays: 8 }),
      ];
      const w = assembleWorld(sources({ plants: many }));
      const drawn = w.nodes.filter(n => n.id.startsWith('n-plant-')).map(n => n.id);
      expect(drawn).toContain('n-plant-2'); // issues → ranked first
    });
  });

  describe('empty / unknown states (C22-C25)', () => {
    it('marks reminders empty when nothing is due', () => {
      const w = assembleWorld(sources({ dashboard: { ...sources().dashboard, overdueReminders: [], todayReminders: [] } }));
      expect(w.nodes.find(n => n.id === 'n-reminders')!.state).toBe('empty');
    });
    it('marks a plant of unknown health as unknown', () => {
      const w = assembleWorld(sources({ plants: [plant(1, { healthStatus: 'UNKNOWN' })] }));
      expect(w.nodes.find(n => n.id === 'n-plant-1')!.state).toBe('unknown');
    });
  });

  describe('determinism (C7)', () => {
    it('produces identical nodes + edges + cells for identical input', () => {
      expect(assembleWorld(sources())).toEqual(assembleWorld(sources()));
    });
    it('lays out columns by breadth-first depth', () => {
      const w = assembleWorld(sources());
      const garden = w.nodes.find(n => n.id === 'n-garden')!;
      const account = w.nodes.find(n => n.id === 'n-account')!;
      // garden is the root (depth 0 → col 0); account is one hop out (depth 1 → col 2)
      expect(garden.cell.col).toBe(0);
      expect(account.cell.col).toBe(2);
    });
  });
});
