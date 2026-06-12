import { Component, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PlantService } from '../../services/plant.service';
import { PlantResponse } from '../../models/plant.model';
import { PageResponse } from '../../../../core/models/api-response.model';

@Component({
  selector: 'app-plant-list',
  templateUrl: './plant-list.component.html',
  styleUrls: ['./plant-list.component.scss'],
})
export class PlantListComponent implements OnInit {
  plants: PlantResponse[] = [];
  page: PageResponse<PlantResponse> | null = null;
  loading = true;
  currentPage = 0;
  pageSize = 20;
  skeletons = Array(6);

  constructor(
    private readonly plantService: PlantService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.loadPlants();
  }

  loadPlants(): void {
    this.loading = true;
    this.plantService.getPlants(this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        this.page = res.data;
        this.plants = res.data?.content ?? [];
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Failed to load plants. Is the backend running?', 'Dismiss', { duration: 5000 });
        this.loading = false;
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPlants();
  }

  onArchive(id: number): void {
    this.plantService.archivePlant(id).subscribe({
      next: () => {
        this.snackBar.open('Plant archived.', 'Undo', { duration: 4000 });
        this.loadPlants();
      },
      error: () => {
        this.snackBar.open('Could not archive plant.', 'Dismiss', { duration: 4000 });
      },
    });
  }
}
