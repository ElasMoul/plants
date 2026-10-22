import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { PlantGroupService } from './plant-group.service';
import { PlantService } from './plant.service';

describe('PlantGroupService (PLA-110)', () => {
  function build() {
    const http = {
      get: jest.fn(() => of({})),
      post: jest.fn(() => of({})),
      put: jest.fn(() => of({})),
      delete: jest.fn(() => of(undefined)),
    };
    return { http, service: new PlantGroupService(http as unknown as HttpClient) };
  }

  it('creates, renames and deletes a group', () => {
    const { http, service } = build();
    service.createGroup('Greenhouse').subscribe();
    service.renameGroup(3, 'Bed').subscribe();
    service.deleteGroup(3).subscribe();
    expect(http.post).toHaveBeenCalledWith(expect.stringMatching(/\/groups$/), { name: 'Greenhouse' });
    expect(http.put).toHaveBeenCalledWith(expect.stringMatching(/\/groups\/3$/), { name: 'Bed' });
    expect(http.delete).toHaveBeenCalledWith(expect.stringMatching(/\/groups\/3$/));
  });

  it('posts the bulk watered/fed action to the group', () => {
    const { http, service } = build();
    service.logCare(3, 'WATERED').subscribe();
    service.logCare(3, 'FED').subscribe();
    expect(http.post).toHaveBeenCalledWith(expect.stringMatching(/\/groups\/3\/care$/), { action: 'WATERED' });
    expect(http.post).toHaveBeenCalledWith(expect.stringMatching(/\/groups\/3\/care$/), { action: 'FED' });
  });

  it('assigns a plant, and clears the group with null', () => {
    const { http, service } = build();
    service.assignPlant(9, 3).subscribe();
    service.assignPlant(9, null).subscribe();
    expect(http.put).toHaveBeenCalledWith(expect.stringMatching(/\/plants\/9\/group$/), { groupId: 3 });
    expect(http.put).toHaveBeenCalledWith(expect.stringMatching(/\/plants\/9\/group$/), { groupId: null });
  });

  it('sends groupId on the plant list only when filtering', () => {
    const http = { get: jest.fn(() => of({})) };
    const plants = new PlantService(http as unknown as HttpClient);
    plants.getPlants(0, 20, undefined, 4).subscribe();
    plants.getPlants(0, 20).subscribe();
    const [, filtered] = http.get.mock.calls[0] as unknown as [string, { params: { get: (k: string) => string | null } }];
    const [, unfiltered] = http.get.mock.calls[1] as unknown as [string, { params: { get: (k: string) => string | null } }];
    expect(filtered.params.get('groupId')).toBe('4');
    expect(unfiltered.params.get('groupId')).toBeNull();
  });
});