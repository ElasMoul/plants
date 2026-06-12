import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { IdentificationHomeComponent } from './identification-home/identification-home.component';

const routes: Routes = [
  { path: '', component: IdentificationHomeComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class IdentificationRoutingModule {}
