import { of, throwError } from 'rxjs';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { UserService } from '@plantpal/shared-core';
import { PlantNetConfigService } from '../../services/plantnet-config.service';
import { DataExportService } from '../../services/data-export.service';
import { FR } from '../../../../shared/i18n/fr';
import { AR } from '../../../../shared/i18n/ar';
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

  it('has a distinct FR and AR translation for every new string', () => {
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
      expect(AR[key]).toBeTruthy();
      expect(AR[key]).not.toBe(key);
    }
  });
});


describe('PreferencesPageComponent email verification (PLA-26)', () => {
  function build(prefs: object, resend: unknown = of({ success: true })) {
    const userService = {
      getPreferences: jest.fn(() => of({ data: prefs })),
      resendVerificationEmail: jest.fn(() => resend),
    };
    const plantnet = {
      getProjects: jest.fn(() => of([])),
      getLanguages: jest.fn(() => of([])),
      getQuota: jest.fn(() => of(null)),
    };
    const component = new PreferencesPageComponent(
      {} as Location, {} as Router,
      userService as unknown as UserService, plantnet as unknown as PlantNetConfigService,
      {} as DataExportService,
    );
    component.ngOnInit();
    return { component, userService };
  }

  it('shows the status from the preferences response', () => {
    expect(build({ emailVerified: false }).component.emailVerified).toBe(false);
    expect(build({ emailVerified: true }).component.emailVerified).toBe(true);
  });

  it('shows no status when the server did not say (stale cached preferences)', () => {
    expect(build({}).component.emailVerified).toBeNull();
  });

  it('resends and reports success, a 429 wait, or a failure', () => {
    const ok = build({ emailVerified: false });
    ok.component.resendVerification();
    expect(ok.component.verificationResult).toBe('sent');

    const wait = build({ emailVerified: false }, throwError(() => ({ status: 429 })));
    wait.component.resendVerification();
    expect(wait.component.verificationResult).toBe('wait');

    const failed = build({ emailVerified: false }, throwError(() => ({ status: 500 })));
    failed.component.resendVerification();
    expect(failed.component.verificationResult).toBe('failed');
    expect(failed.component.verificationSending).toBe(false);
  });
});

describe('PreferencesPageComponent account management (PLA-24)', () => {
  function build(overrides: Record<string, unknown> = {}) {
    const userService = {
      getPreferences: jest.fn(() =>
        of({ data: { firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.test' } })),
      updateProfile: jest.fn(() => of({ data: { firstName: 'Grace', lastName: 'Hopper' } })),
      changePassword: jest.fn(() => of({ success: true })),
      requestEmailChange: jest.fn(() => of({ success: true })),
      ...overrides,
    };
    const plantnet = {
      getProjects: jest.fn(() => of([])),
      getLanguages: jest.fn(() => of([])),
      getQuota: jest.fn(() => of(null)),
    };
    const component = new PreferencesPageComponent(
      {} as Location, {} as Router,
      userService as unknown as UserService, plantnet as unknown as PlantNetConfigService,
      {} as DataExportService,
    );
    component.ngOnInit();
    return { component, userService };
  }

  const badRequest = (message: string) => throwError(() => ({ status: 400, error: { message } }));

  it('seeds the profile and saves trimmed names', () => {
    const { component, userService } = build();
    expect(component.firstName).toBe('Ada');
    expect(component.email).toBe('ada@example.test');
    component.firstName = ' Grace ';
    component.lastName = 'Hopper';
    component.saveProfile();
    expect(userService.updateProfile).toHaveBeenCalledWith('Grace', 'Hopper');
    expect(component.profileResult).toBe('saved');
  });

  it('shows the server validation message for a refused profile', () => {
    const { component } = build({ updateProfile: jest.fn(() => badRequest('size must be between 0 and 100')) });
    component.saveProfile();
    expect(component.profileResult).toBe('failed');
    expect(component.profileError).toBe('size must be between 0 and 100');
  });

  it('shows a wrong current password and keeps what was typed', () => {
    const { component } = build({ changePassword: jest.fn(() => badRequest('Current password is incorrect')) });
    component.currentPassword = 'wrong';
    component.newPassword = 'brand-new-pass';
    component.confirmPassword = 'brand-new-pass';
    component.submitPasswordChange();
    expect(component.passwordResult).toBe('failed');
    expect(component.passwordError).toBe('Current password is incorrect');
    expect(component.currentPassword).toBe('wrong');
  });

  it('does not send a password change whose confirmation differs', () => {
    const { component, userService } = build();
    component.currentPassword = 'current-pass';
    component.newPassword = 'brand-new-pass';
    component.confirmPassword = 'something-else';
    component.submitPasswordChange();
    expect(userService.changePassword).not.toHaveBeenCalled();
  });

  it('changes the password, clears the fields and stays on the page', () => {
    const { component, userService } = build();
    component.currentPassword = 'current-pass';
    component.newPassword = 'brand-new-pass';
    component.confirmPassword = 'brand-new-pass';
    component.submitPasswordChange();
    expect(userService.changePassword).toHaveBeenCalledWith('current-pass', 'brand-new-pass');
    expect(component.passwordResult).toBe('changed');
    expect(component.newPassword).toBe('');
  });

  it('starts an email change and shows the pending notice, or the server refusal', () => {
    const ok = build();
    ok.component.newEmail = ' new@example.test ';
    ok.component.emailPassword = 'current-pass';
    ok.component.submitEmailChange();
    expect(ok.userService.requestEmailChange).toHaveBeenCalledWith('new@example.test', 'current-pass');
    expect(ok.component.emailResult).toBe('sent');
    expect(ok.component.pendingEmail).toBe('new@example.test');

    const taken = build({ requestEmailChange: jest.fn(() => badRequest('Email already registered')) });
    taken.component.newEmail = 'taken@example.test';
    taken.component.emailPassword = 'current-pass';
    taken.component.submitEmailChange();
    expect(taken.component.emailResult).toBe('failed');
    expect(taken.component.pendingEmail).toBeNull();
  });

  const NEW_KEYS = [
    'Profile', 'Save profile', 'Profile saved.', 'Could not save your profile. Please try again.',
    'Email address', 'Current address: {0}', 'New email address', 'Current password', 'Change email',
    'Could not change your email. Please try again.', 'New password', 'Change password',
    'Changing your password signs you out everywhere else.',
    'Password changed. Your other sessions were signed out.',
    'Could not change your password. Please try again.', 'Current password is incorrect',
    'That is already your email address', 'Email already registered',
    'Check your inbox at {0} and open the link to confirm the change. Until then you keep signing in with your current address.',
  ];

  it('has an FR translation (with the same placeholders) for every new string', () => {
    for (const key of NEW_KEYS) {
      expect(FR[key]).toBeTruthy();
      expect(FR[key]).not.toBe(key);
      expect(FR[key].includes('{0}')).toBe(key.includes('{0}'));
    }
  });

  it('has an Arabic entry per new string, and an unknown key still falls back to its English text', () => {
    for (const key of NEW_KEYS) {
      expect(AR[key]).toBeTruthy();
      expect(AR[key].includes('{0}')).toBe(key.includes('{0}'));
    }
    const unknown = 'A string nobody translated';
    expect(AR[unknown] ?? unknown).toBe(unknown);
  });
});
