import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { IdentificationPageComponent } from './pages/identification-page/identification-page.component';

const routes: Routes = [
  { path: '', component: IdentificationPageComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class IdentificationRoutingModule {}
