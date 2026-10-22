import { of, throwError } from 'rxjs';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { UserService } from '@plantpal/shared-core';
import { PlantNetConfigService } from '../../services/plantnet-config.service';
import { DataExportService } from '../../services/data-export.service';
import { FR } from '../../../../shared/i18n/fr';
import { PreferencesPageComponent } from './preferences-page.component';

describe('PreferencesPageComponent region (PLA-107)', () => {
  function build(prefs: object, dataExport: object = {}) {
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
      dataExport as unknown as DataExportService,
    );
    component.ngOnInit();
    return { component, userService, dataExport };
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

describe('PreferencesPageComponent data export (PLA-112)', () => {
  const blob = new Blob(['zip']);
  const created = { id: 'abc', expiresAt: '2026-10-23T00:00:00Z', sizeBytes: 3, retentionHours: 24 };

  function build(requestExport: jest.Mock) {
    const dataExport = { requestExport, save: jest.fn() };
    const userService = { getPreferences: jest.fn(() => of({ data: {} })) };
    const plantnet = {
      getProjects: jest.fn(() => of([])),
      getLanguages: jest.fn(() => of([])),
      getQuota: jest.fn(() => of(null)),
    };
    const component = new PreferencesPageComponent(
      {} as Location, {} as Router,
      userService as unknown as UserService, plantnet as unknown as PlantNetConfigService,
      dataExport as unknown as DataExportService,
    );
    return { component, dataExport };
  }

  it('requests the export and hands the blob to the browser', () => {
    const { component, dataExport } = build(jest.fn(() => of({ export: created, blob })));
    component.exportData();
    expect(dataExport.requestExport).toHaveBeenCalledTimes(1);
    expect(dataExport.save).toHaveBeenCalledWith(blob);
    expect(component.exportDone).toBe(true);
    expect(component.exportBusy).toBe(false);
    expect(component.exportError).toBeNull();
  });

  it('shows the rate-limit state on HTTP 429', () => {
    const { component, dataExport } = build(jest.fn(() => throwError(() => ({ status: 429 }))));
    component.exportData();
    expect(component.exportError).toBe('rate-limited');
    expect(component.exportBusy).toBe(false);
    expect(dataExport.save).not.toHaveBeenCalled();
  });

  it('shows a generic failure for any other error', () => {
    const { component } = build(jest.fn(() => throwError(() => ({ status: 500 }))));
    component.exportData();
    expect(component.exportError).toBe('failed');
  });

  it('has a distinct FR translation for every new string', () => {
    const keys = [
      'Export my data',
      'Download a ZIP with your plants, care log, scan history and photos (JSON and CSV).',
      'The file is deleted from our servers {0} hours after it is created.',
      'Preparing your export…',
      'You have requested too many exports. Please try again later.',
      'Could not create your export. Please try again.',
    ];
    for (const key of keys) {
      expect(FR[key]).toBeTruthy();
      expect(FR[key]).not.toBe(key);
    }
  });
});
