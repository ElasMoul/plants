import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { PlantRoutingModule } from './plant-routing.module';
import { PlantListComponent } from './plant-list/plant-list.component';

@NgModule({
  declarations: [PlantListComponent],
  imports: [SharedModule, PlantRoutingModule],
})
export class PlantModule {}
