import { of } from 'rxjs';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { UserService } from '@plantpal/shared-core';
import { PlantNetConfigService } from '../../services/plantnet-config.service';
import { PreferencesPageComponent } from './preferences-page.component';

describe('PreferencesPageComponent region (PLA-107)', () => {
  function build(prefs: object) {
    const userService = {
      getPreferences: jest.fn(() => of({ data: prefs })),
      updateRegionPreference: jest.fn(() => of({ data: {} })),
    };
    const plantnet = {
      getProjects: jest.fn(() => of([])),
      getLanguages: jest.fn(() => of([])),
      getQuota: jest.fn(() => of(null)),
    };
    const component = new PreferencesPageComponent(
      {} as Location, {} as Router,
      userService as unknown as UserService, plantnet as unknown as PlantNetConfigService,
    );
    component.ngOnInit();
    return { component, userService };
  }

  it('shows an empty region for users who never chose one', () => {
    const { component } = build({});
    expect(component.regionCountry).toBeNull();
    expect(component.regionClimate).toBeNull();
  });

  it('seeds, edits and saves only the country code and climate zone', () => {
    const { component, userService } = build({ regionCountry: 'FR', regionClimate: 'TEMPERATE' });
    expect(component.regionCountry).toBe('FR');
    component.regionCountry = 'BR';
    component.regionClimate = 'TROPICAL';
    component.saveRegion();
    expect(userService.updateRegionPreference).toHaveBeenCalledWith('BR', 'TROPICAL');
    expect(component.regionSaved).toBe(true);
  });
});
