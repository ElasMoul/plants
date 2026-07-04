import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { IdentificationPageComponent } from './pages/identification-page/identification-page.component';
import { IdentificationDetailPageComponent } from './pages/identification-detail-page/identification-detail-page.component';

const routes: Routes = [
  { path: '', component: IdentificationPageComponent },
  { path: ':id', component: IdentificationDetailPageComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class IdentificationRoutingModule {}
