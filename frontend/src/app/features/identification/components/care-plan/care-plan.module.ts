import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { CareCardComponent } from './care-card.component';
import { CarePlanComponent } from './care-plan.component';

@NgModule({
  declarations: [CareCardComponent, CarePlanComponent],
  imports: [CommonModule, MatIconModule, MatDividerModule],
  exports: [CareCardComponent, CarePlanComponent],
})
export class CarePlanModule {}
