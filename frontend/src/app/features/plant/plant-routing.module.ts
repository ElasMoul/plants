import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PlantListComponent } from './components/plant-list/plant-list.component';
import { PlantFormComponent } from './components/plant-form/plant-form.component';
import { PlantDetailComponent } from './components/plant-detail/plant-detail.component';

const routes: Routes = [
  { path: '', component: PlantListComponent },
  { path: 'new', component: PlantFormComponent },
  { path: ':id/edit', component: PlantFormComponent },
  { path: ':id', component: PlantDetailComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class PlantRoutingModule {}
