import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SpeciesListComponent } from './pages/species-list/species-list.component';
import { SpeciesDetailComponent } from './pages/species-detail/species-detail.component';

const routes: Routes = [
  { path: '', component: SpeciesListComponent },
  { path: 'species/:id', component: SpeciesDetailComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SpeciesRoutingModule {}
