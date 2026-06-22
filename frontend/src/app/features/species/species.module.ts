import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule } from '@angular/material/snack-bar';

import { SharedModule } from '../../shared/shared.module';
import { SpeciesRoutingModule } from './species-routing.module';
import { SpeciesService } from './services/species.service';
import { IdentificationService } from '../identification/services/identification.service';
import { PlantService } from '../plant/services/plant.service';
import { CarePlanModule } from '../identification/components/care-plan/care-plan.module';
import { SpeciesListComponent } from './pages/species-list/species-list.component';
import { SpeciesDetailComponent } from './pages/species-detail/species-detail.component';
import { SpeciesCardComponent } from './components/species-card/species-card.component';
import { SpeciesPlantRowComponent } from './components/species-plant-row/species-plant-row.component';

@NgModule({
  declarations: [
    SpeciesListComponent,
    SpeciesDetailComponent,
    SpeciesCardComponent,
    SpeciesPlantRowComponent,
  ],
  imports: [
    CommonModule,
    RouterModule,
    SharedModule,
    SpeciesRoutingModule,
    CarePlanModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatDialogModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  providers: [SpeciesService, IdentificationService, PlantService],
})
export class SpeciesModule {}
