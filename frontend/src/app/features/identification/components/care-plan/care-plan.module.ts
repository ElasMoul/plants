import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SharedModule } from '../../../../shared/shared.module';
import { CareCardComponent } from './care-card.component';
import { CarePlanComponent } from './care-plan.component';
import { SetReminderDialogComponent } from './set-reminder-dialog.component';
import { PhotoAnnotatorComponent } from '../photo-annotator/photo-annotator.component';
import { AnnotationListComponent } from '../annotation-list/annotation-list.component';
import { DiseaseDetailPanelComponent } from '../disease-detail-panel/disease-detail-panel.component';

@NgModule({
  declarations: [
    CareCardComponent,
    CarePlanComponent,
    SetReminderDialogComponent,
    PhotoAnnotatorComponent,
    AnnotationListComponent,
    DiseaseDetailPanelComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatIconModule,
    MatDividerModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    SharedModule,
  ],
  exports: [
    CareCardComponent,
    CarePlanComponent,
    PhotoAnnotatorComponent,
    AnnotationListComponent,
    DiseaseDetailPanelComponent,
  ],
})
export class CarePlanModule {}
