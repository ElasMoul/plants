import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';

import { SpeciesRoutingModule } from './species-routing.module';
import { SpeciesService } from './services/species.service';
import { IdentificationService } from '../identification/services/identification.service';
import { SpeciesListComponent } from './pages/species-list/species-list.component';
import { SpeciesCardComponent } from './components/species-card/species-card.component';

@NgModule({
  declarations: [SpeciesListComponent, SpeciesCardComponent],
  imports: [
    CommonModule,
    RouterModule,
    SpeciesRoutingModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatDialogModule,
  ],
  providers: [SpeciesService, IdentificationService],
})
export class SpeciesModule {}
