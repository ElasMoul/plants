import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ClimateZone, UserService } from '@plantpal/shared-core';
import { CLIMATE_ZONES, CountryOption, countryOptions } from '../../../../shared/region/region';
import { readLanguage } from '../../../../shared/i18n/language.service';
import {
  PlantNetConfigService,
  PlantNetProject,
  PlantNetQuota,
} from '../../services/plantnet-config.service';
import {
  DataExportService,
  DEFAULT_EXPORT_RETENTION_HOURS,
} from '../../services/data-export.service';

@Component({
    selector: 'app-preferences-page',
    templateUrl: './preferences-page.component.html',
    styleUrls: ['./preferences-page.component.scss'],
    standalone: false
})
export class PreferencesPageComponent implements OnInit {
  plantnetProjects: PlantNetProject[] = [];
  plantnetLanguages: string[] = [];
  plantnetQuota: PlantNetQuota | null = null;
  selectedProject = 'all';
  selectedLang = 'en';
  plantnetSaving = false;
  plantnetSaved = false;
  plantnetLoadError = false;

  readonly climateZones = CLIMATE_ZONES;
  countries: CountryOption[] = countryOptions(readLanguage());
  regionCountry: string | null = null;
  regionClimate: ClimateZone | null = null;
  regionSaving = false;
  regionSaved = false;

  emailVerified: boolean | null = null;
  verificationSending = false;
  verificationResult: 'sent' | 'wait' | 'failed' | null = null;

  businessTier = false;
  businessTierSaving = false;

  exportBusy = false;
  exportDone = false;
  exportError: 'rate-limited' | 'failed' | null = null;
  exportRetentionHours = DEFAULT_EXPORT_RETENTION_HOURS;

  constructor(
    private readonly location: Location,
    private readonly router: Router,
    private readonly userService: UserService,
    private readonly plantNetConfigService: PlantNetConfigService,
    private readonly dataExportService: DataExportService,
  ) {}

  ngOnInit(): void {
    forkJoin({
      prefs: this.userService.getPreferences(),
      projects: this.plantNetConfigService.getProjects(),
      languages: this.plantNetConfigService.getLanguages(),
      quota: this.plantNetConfigService.getQuota().pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ prefs, projects, languages, quota }) => {
        this.plantnetProjects = projects;
        this.plantnetLanguages = languages;
        this.plantnetQuota = quota;
        // Seed dropdowns from stored preferences
        this.selectedProject = prefs.data.plantnetProject ?? 'all';
        this.selectedLang = prefs.data.plantnetLang ?? 'en';
        this.businessTier = prefs.data.businessTier ?? false;
        this.emailVerified = prefs.data.emailVerified ?? null;
        this.regionCountry = prefs.data.regionCountry ?? null;
        this.regionClimate = prefs.data.regionClimate ?? null;
      },
      error: () => {
        this.plantnetLoadError = true;
      },
    });
  }

  savePlantNetPreferences(): void {
    this.plantnetSaving = true;
    this.plantnetSaved = false;
    this.userService
      .updatePlantNetPreferences(this.selectedProject, this.selectedLang)
      .subscribe({
        next: () => {
          this.plantnetSaving = false;
          this.plantnetSaved = true;
        },
        error: () => {
          this.plantnetSaving = false;
        },
      });
  }

  resendVerification(): void {
    this.verificationSending = true;
    this.verificationResult = null;
    this.userService.resendVerificationEmail().subscribe({
      next: () => {
        this.verificationSending = false;
        this.verificationResult = 'sent';
      },
      error: (err: { status?: number }) => {
        this.verificationSending = false;
        this.verificationResult = err.status === 429 ? 'wait' : 'failed';
      },
    });
  }

  saveRegion(): void {
    this.regionSaving = true;
    this.regionSaved = false;
    this.userService
      .updateRegionPreference(this.regionCountry ?? undefined, this.regionClimate ?? undefined)
      .subscribe({
        next: () => {
          this.regionSaving = false;
          this.regionSaved = true;
        },
        error: () => {
          this.regionSaving = false;
        },
      });
  }

  onBusinessTierChange(event: MatSlideToggleChange): void {
    const previous = this.businessTier;
    const next = event.checked;
    this.businessTier = next;
    this.businessTierSaving = true;
    this.userService.updateBusinessTierPreference(next).subscribe({
      next: () => {
        this.businessTierSaving = false;
      },
      error: () => {
        this.businessTier = previous;
        this.businessTierSaving = false;
      },
    });
  }

  exportData(): void {
    this.exportBusy = true;
    this.exportDone = false;
    this.exportError = null;
    this.dataExportService.requestExport().subscribe({
      next: ({ export: created, blob }) => {
        this.exportRetentionHours = created.retentionHours;
        this.dataExportService.save(blob);
        this.exportBusy = false;
        this.exportDone = true;
      },
      error: (err: { status?: number }) => {
        this.exportBusy = false;
        this.exportError = err?.status === 429 ? 'rate-limited' : 'failed';
      },
    });
  }

  projectLabel(project: PlantNetProject): string {
    return project.name || project.id;
  }

  close(): void {
    // history.state.navigationId > 1 means we actually navigated here (not a fresh tab/refresh
    // landing directly on /preferences) — only then is location.back() safe to use.
    if (history.state?.navigationId > 1) {
      this.location.back();
    } else {
      this.router.navigate(['/home']);
    }
  }
}
