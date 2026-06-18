import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { GardenDashboardComponent } from './pages/garden-dashboard/garden-dashboard.component';

const routes: Routes = [
  { path: '', component: GardenDashboardComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DashboardRoutingModule {}
