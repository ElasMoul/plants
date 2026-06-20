import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PlantFormComponent } from './components/plant-form/plant-form.component';
import { PlantDetailComponent } from './components/plant-detail/plant-detail.component';

// '' (plant list) deliberately not routed here anymore — the Garden list now lives at
// /garden (SpeciesModule, T6.5). PlantListComponent stays declared in PlantModule, unrouted,
// since it's not yet confirmed dead for every future Phase 6 task.
const routes: Routes = [
  { path: 'new', component: PlantFormComponent },
  { path: ':id/edit', component: PlantFormComponent },
  { path: ':id', component: PlantDetailComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class PlantRoutingModule {}
