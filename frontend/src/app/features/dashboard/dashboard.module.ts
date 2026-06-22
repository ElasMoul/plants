import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { SharedModule } from '../../shared/shared.module';
import { DashboardRoutingModule } from './dashboard-routing.module';
import { GardenDashboardComponent } from './pages/garden-dashboard/garden-dashboard.component';
import { HomeComponent } from './pages/home/home.component';
import { DashboardService } from './services/dashboard.service';
import { IdentificationService } from '../identification/services/identification.service';
import { BatchScanService } from '../identification/services/batch-scan.service';
import { PlantService } from '../plant/services/plant.service';

@NgModule({
  declarations: [GardenDashboardComponent, HomeComponent],
  imports: [SharedModule, DashboardRoutingModule, MatDialogModule],
  providers: [DashboardService, IdentificationService, BatchScanService, PlantService],
})
export class DashboardModule {}
