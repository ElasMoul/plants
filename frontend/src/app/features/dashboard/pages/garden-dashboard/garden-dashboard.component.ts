import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { DashboardService } from '../../services/dashboard.service';
import { CareType, DashboardResponse, PlantHealthTrendDto } from '../../models/dashboard.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

const CARE_ICONS: Record<CareType, string> = {
  WATERING: 'water_drop',
  LIGHT: 'wb_sunny',
  HUMIDITY: 'opacity',
  TEMPERATURE: 'thermostat',
  FERTILIZING: 'eco',
  REPOTTING: 'yard',
  PRUNING: 'content_cut',
  PEST: 'pest_control',
  SEASONAL: 'calendar_month',
  BEGINNER_TIP: 'lightbulb',
};

@Component({
  selector: 'app-garden-dashboard',
  templateUrl: './garden-dashboard.component.html',
  styleUrls: ['./garden-dashboard.component.scss'],
})
export class GardenDashboardComponent implements OnInit, OnDestroy {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  readonly skeletonRows = [1, 2, 3];

  dashboard: DashboardResponse | null = null;
  loading = true;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly dashboardService: DashboardService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.dashboardService.getDashboard()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.dashboard = res.data;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get notableTrends(): PlantHealthTrendDto[] {
    return this.dashboard?.healthTrends.filter(t => t.trend !== 'STABLE') ?? [];
  }

  careIcon(careType: CareType): string {
    return CARE_ICONS[careType];
  }

  goToPlant(plantId: number): void {
    this.router.navigate(['/plants', plantId]);
  }
}
