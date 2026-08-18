import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { UserService } from '@plantpal/shared-core';
import {
  PlantNetConfigService,
  PlantNetProject,
  PlantNetQuota,
} from '../../services/plantnet-config.service';

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

  businessTier = false;
  businessTierSaving = false;

  constructor(
    private readonly location: Location,
    private readonly router: Router,
    private readonly userService: UserService,
    private readonly plantNetConfigService: PlantNetConfigService,
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
