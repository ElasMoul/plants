import { NgModule } from '@angular/core';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule } from '@angular/material/dialog';
import { SharedModule } from '../../shared/shared.module';
import { IdentificationRoutingModule } from './identification-routing.module';
import { CarePlanModule } from './components/care-plan/care-plan.module';
import { PhotoUploadComponent } from './components/photo-upload/photo-upload.component';
import { PreviewCardComponent } from './components/preview-card/preview-card.component';
import { IdentificationPreviewSectionComponent } from './components/identification-preview-section/identification-preview-section.component';
import { IdentificationListComponent } from './components/identification-list/identification-list.component';
import { IdentificationUploadDialogComponent } from './components/identification-upload-dialog/identification-upload-dialog.component';
import { IdentificationPageComponent } from './pages/identification-page/identification-page.component';
import { IdentificationDetailPageComponent } from './pages/identification-detail-page/identification-detail-page.component';
import { SpeciesConfirmStepComponent } from './components/species-confirm-step/species-confirm-step.component';
import { PlantSelectStepComponent } from './components/plant-select-step/plant-select-step.component';
import { IdentificationService } from './services/identification.service';
import { PlantService } from '../plant/services/plant.service';
import { ReminderService } from '../reminder/services/reminder.service';
import { TreatmentPlanService } from '../reminder/services/treatment-plan.service';
import { TreatmentService } from '../plant/services/treatment.service';

@NgModule({
  declarations: [
    PhotoUploadComponent,
    PreviewCardComponent,
    IdentificationPreviewSectionComponent,
    IdentificationListComponent,
    IdentificationUploadDialogComponent,
    IdentificationPageComponent,
    IdentificationDetailPageComponent,
    SpeciesConfirmStepComponent,
    PlantSelectStepComponent,
  ],
  imports: [
    SharedModule,
    IdentificationRoutingModule,
    CarePlanModule,
    MatSelectModule,
    MatProgressBarModule,
    MatDialogModule,
  ],
  providers: [
    IdentificationService,
    PlantService,
    ReminderService,
    TreatmentPlanService,
    TreatmentService,
  ],
})
export class IdentificationModule {}
