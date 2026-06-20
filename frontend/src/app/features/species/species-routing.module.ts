import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SpeciesListComponent } from './pages/species-list/species-list.component';

const routes: Routes = [
  { path: '', component: SpeciesListComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SpeciesRoutingModule {}
