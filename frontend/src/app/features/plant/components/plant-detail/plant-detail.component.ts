import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PlantService } from '../../services/plant.service';
import { PlantResponse } from '../../models/plant.model';

@Component({
  selector: 'app-plant-detail',
  templateUrl: './plant-detail.component.html',
  styleUrls: ['./plant-detail.component.scss'],
})
export class PlantDetailComponent implements OnInit {
  plant: PlantResponse | null = null;
  loading = true;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly plantService: PlantService,
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
