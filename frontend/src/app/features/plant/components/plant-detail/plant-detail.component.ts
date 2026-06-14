import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PlantService } from '../../services/plant.service';
import { IdentificationService } from '../../../identification/services/identification.service';
import { PlantResponse } from '../../models/plant.model';
import { CarePlanDto } from '../../../identification/models/identification.model';

@Component({
  selector: 'app-plant-detail',
  templateUrl: './plant-detail.component.html',
  styleUrls: ['./plant-detail.component.scss'],
})
export class PlantDetailComponent implements OnInit, OnDestroy {
  plant: PlantResponse | null = null;
  loading = true;
  latestCarePlan: CarePlanDto | null = null;
  hasIdentification = false;
  carePlanLoaded = false;

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
          this.latestCarePlan = items.length > 0 ? items[0].carePlan : null;
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
