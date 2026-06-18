import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { DashboardRoutingModule } from './dashboard-routing.module';
import { GardenDashboardComponent } from './pages/garden-dashboard/garden-dashboard.component';
import { DashboardService } from './services/dashboard.service';

@NgModule({
  declarations: [GardenDashboardComponent],
  imports: [SharedModule, DashboardRoutingModule],
  providers: [DashboardService],
})
export class DashboardModule {}
