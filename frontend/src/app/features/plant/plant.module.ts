import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { MatBottomSheetModule } from '@angular/material/bottom-sheet';

import { PlantRoutingModule } from './plant-routing.module';
import { CarePlanModule } from '../identification/components/care-plan/care-plan.module';
import { CareLogModule } from '../reminder/components/care-log/care-log.module';
import { PlantService } from './services/plant.service';
import { TreatmentService } from './services/treatment.service';
import { IdentificationService } from '../identification/services/identification.service';
import { CareLogService } from '../reminder/services/care-log.service';
import { ReminderService } from '../reminder/services/reminder.service';
import { TreatmentPlanService } from '../reminder/services/treatment-plan.service';
import { PlantListComponent } from './components/plant-list/plant-list.component';
import { PlantCardComponent } from './components/plant-card/plant-card.component';
import { PlantFormComponent } from './components/plant-form/plant-form.component';
import { PlantDetailComponent } from './components/plant-detail/plant-detail.component';
import { PlantPhotoTimelineComponent } from './components/plant-photo-timeline/plant-photo-timeline.component';
import { PlantActionsSheetComponent } from './components/plant-actions-sheet/plant-actions-sheet.component';
import { PlantScanHistorySheetComponent } from './components/plant-scan-history-sheet/plant-scan-history-sheet.component';

@NgModule({
  declarations: [
    PlantListComponent,
    PlantCardComponent,
    PlantFormComponent,
    PlantDetailComponent,
    PlantPhotoTimelineComponent,
    PlantActionsSheetComponent,
    PlantScanHistorySheetComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    PlantRoutingModule,
    CarePlanModule,
    CareLogModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSelectModule,
    MatChipsModule,
    MatSnackBarModule,
    MatDividerModule,
    MatTooltipModule,
    MatDialogModule,
    MatBottomSheetModule,
  ],
  providers: [PlantService, TreatmentService, IdentificationService, CareLogService, ReminderService, TreatmentPlanService],
})
export class PlantModule {}
