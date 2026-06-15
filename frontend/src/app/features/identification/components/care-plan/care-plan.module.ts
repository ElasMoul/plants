import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { CareCardComponent } from './care-card.component';
import { CarePlanComponent } from './care-plan.component';
import { PhotoAnnotatorComponent } from '../photo-annotator/photo-annotator.component';

@NgModule({
  declarations: [CareCardComponent, CarePlanComponent, PhotoAnnotatorComponent],
  imports: [CommonModule, MatIconModule, MatDividerModule, MatButtonModule],
  exports: [CareCardComponent, CarePlanComponent, PhotoAnnotatorComponent],
})
export class CarePlanModule {}
