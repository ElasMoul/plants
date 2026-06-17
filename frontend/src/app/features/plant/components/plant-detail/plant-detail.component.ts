import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PlantService } from '../../services/plant.service';
import { IdentificationService } from '../../../identification/services/identification.service';
import { PlantResponse } from '../../models/plant.model';
import { AnnotationRegion, CarePlanDto } from '../../../identification/models/identification.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';


@Component({
  selector: 'app-plant-detail',
  templateUrl: './plant-detail.component.html',
  styleUrls: ['./plant-detail.component.scss'],
})
export class PlantDetailComponent implements OnInit, OnDestroy {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  plant: PlantResponse | null = null;
  loading = true;
  latestCarePlan: CarePlanDto | null = null;
  latestPhotoUrl: string | null = null;
  latestAnnotationRegions: AnnotationRegion[] | null = null;
  latestIdentificationId: number | null = null;
  hasIdentification = false;
  carePlanLoaded = false;
  selectedRegionIndex: number | null = null;
  selectedRegion: AnnotationRegion | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly plantService: PlantService,
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));

    this.plantService.getPlant(id).subscribe({
      next: (res) => {
        this.plant = res.data;
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Plant not found.', 'Dismiss', { duration: 4000 });
        this.router.navigate(['/plants']);
      },
    });

    this.identificationService.getPlantIdentifications(id, 0, 1)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          const items = res.data.content;
          this.hasIdentification = items.length > 0;
          if (items.length > 0) {
            this.latestCarePlan = items[0].carePlan;
            this.latestPhotoUrl = items[0].photoUrl;
            this.latestAnnotationRegions = items[0].annotationRegions;
            this.latestIdentificationId = items[0].id;
          }
          this.carePlanLoaded = true;
        },
        error: () => {
          this.carePlanLoaded = true;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onRegionSelected(index: number | null): void {
    this.selectedRegionIndex = index;
    this.selectedRegion = index !== null && this.latestAnnotationRegions
      ? (this.latestAnnotationRegions[index] ?? null)
      : null;
  }

  onArchive(): void {
    if (!this.plant) return;
    this.plantService.archivePlant(this.plant.id).subscribe({
      next: () => {
        this.snackBar.open('Plant archived.', undefined, { duration: 3000 });
        this.router.navigate(['/plants']);
      },
      error: () => {
        this.snackBar.open('Could not archive plant.', 'Dismiss', { duration: 4000 });
      },
    });
  }
}
