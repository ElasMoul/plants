import { ChangeDetectorRef } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { IdentificationService } from '../../../identification/services/identification.service';
import { PlantGroupService } from '../../services/plant-group.service';
import { PlantService } from '../../services/plant.service';
import { PlantListComponent } from './plant-list.component';

describe('PlantListComponent group filter (PLA-110)', () => {
  function build(groups = [{ id: 4, name: 'Bed', plantCount: 1, createdAt: '' }]) {
    const plantService = { getPlants: jest.fn(() => of({ data: { content: [], totalElements: 0 } })) };
    const groupService = { getGroups: jest.fn(() => of({ data: { content: groups } })) };
    const dialog = { open: jest.fn(() => ({ afterClosed: () => of(true) })) };
    const component = new PlantListComponent(
      plantService as unknown as PlantService,
      groupService as unknown as PlantGroupService,
      {} as IdentificationService,
      { open: jest.fn() } as unknown as MatSnackBar,
      dialog as unknown as MatDialog,
      {} as Router,
      { markForCheck: jest.fn() } as unknown as ChangeDetectorRef,
    );
    component.ngOnInit();
    return { component, plantService, groupService, dialog };
  }

  it('loads all plants and the groups initially', () => {
    const { component, plantService } = build();
    expect(component.groups).toHaveLength(1);
    expect(plantService.getPlants).toHaveBeenLastCalledWith(0, 20, undefined, null);
  });

  it('passes the chosen groupId to the plants call and resets to the first page', () => {
    const { component, plantService } = build();
    component.currentPage = 2;
    component.onGroupFilterChange(4);
    expect(component.currentPage).toBe(0);
    expect(plantService.getPlants).toHaveBeenLastCalledWith(0, 20, undefined, 4);
    component.onGroupFilterChange(null);
    expect(plantService.getPlants).toHaveBeenLastCalledWith(0, 20, undefined, null);
  });

  it('reloads groups and plants after the manager reports a change', () => {
    const { component, plantService, groupService } = build();
    plantService.getPlants.mockClear();
    groupService.getGroups.mockClear();
    component.openGroupManager();
    expect(groupService.getGroups).toHaveBeenCalledTimes(1);
    expect(plantService.getPlants).toHaveBeenCalledTimes(1);
  });

  it('falls back to all plants when the selected group no longer exists', () => {
    const { component, plantService, groupService } = build();
    component.onGroupFilterChange(4);
    groupService.getGroups.mockReturnValueOnce(of({ data: { content: [] } }));
    component.loadGroups();
    expect(component.selectedGroupId).toBeNull();
    expect(plantService.getPlants).toHaveBeenLastCalledWith(0, 20, undefined, null);
  });
});