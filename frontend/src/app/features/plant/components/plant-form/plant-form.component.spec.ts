import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';

import { PlantService } from '../../services/plant.service';
import { PlantFormComponent } from './plant-form.component';

describe('PlantFormComponent placement and light (PLA-107)', () => {
  function build(id: string | null, plant: object = {}) {
    const plantService = {
      createPlant: jest.fn(() => of({ data: { id: 5 } })),
      updatePlant: jest.fn(() => of({ data: { id: 5 } })),
      getPlant: jest.fn(() => of({ data: { nickname: 'Monty', ...plant } })),
    };
    const router = { navigate: jest.fn() } as unknown as Router;
    const route = {
      snapshot: { paramMap: { get: () => id }, queryParams: {} },
    } as unknown as ActivatedRoute;
    const snack = { open: jest.fn() } as unknown as MatSnackBar;
    const component = new PlantFormComponent(
      new FormBuilder(), route, router, plantService as unknown as PlantService, snack,
    );
    component.ngOnInit();
    return { component, plantService };
  }

  it('offers only the fixed placement and light options', () => {
    const { component } = build(null);
    expect(component.placements).toEqual(['INDOOR', 'OUTDOOR', 'GREENHOUSE']);
    expect(component.lightExposures).toEqual(['LOW', 'MEDIUM', 'BRIGHT', 'DIRECT_SUN']);
  });

  it('saves the selected values on create', () => {
    const { component, plantService } = build(null);
    component.form.patchValue({ nickname: 'Monty', placement: 'GREENHOUSE', lightExposure: 'BRIGHT' });
    component.onSubmit();
    expect(plantService.createPlant).toHaveBeenCalledWith(
      expect.objectContaining({ placement: 'GREENHOUSE', lightExposure: 'BRIGHT' }),
    );
  });

  it('loads existing values on edit and saves changed ones', () => {
    const { component, plantService } = build('5', { placement: 'INDOOR', lightExposure: 'LOW' });
    expect(component.form.value.placement).toBe('INDOOR');
    expect(component.form.value.lightExposure).toBe('LOW');
    component.form.patchValue({ placement: 'OUTDOOR', lightExposure: 'DIRECT_SUN' });
    component.onSubmit();
    expect(plantService.updatePlant).toHaveBeenCalledWith(
      5, expect.objectContaining({ placement: 'OUTDOOR', lightExposure: 'DIRECT_SUN' }),
    );
  });

  it('leaves a legacy plant empty and sends nothing for the new fields', () => {
    const { component, plantService } = build('5', { placement: null, lightExposure: null });
    expect(component.form.value.placement).toBeNull();
    component.onSubmit();
    const request = (plantService.updatePlant.mock.calls[0] as unknown[])[1] as Record<string, unknown>;
    expect(request['placement']).toBeUndefined();
    expect(request['lightExposure']).toBeUndefined();
  });
});
