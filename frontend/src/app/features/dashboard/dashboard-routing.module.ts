import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { GardenDashboardComponent } from './pages/garden-dashboard/garden-dashboard.component';
import { HomeComponent } from './pages/home/home.component';

// Mounted at '/home' (see app-routing.module.ts) — '' is the Home landing page, 'overview' is
// the existing fuller health-trends Garden Dashboard (T2.10), kept reachable but no longer
// the default landing. The old '/dashboard' URL redirects to 'home/overview' at the app-routing
// level rather than living here, since this module is only ever mounted under 'home' now.
const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'overview', component: GardenDashboardComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DashboardRoutingModule {}
