import { NgModule } from '@angular/core';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SharedModule } from '../../shared/shared.module';
import { IdentificationRoutingModule } from './identification-routing.module';
import { CarePlanModule } from './components/care-plan/care-plan.module';
import { PhotoUploadComponent } from './components/photo-upload/photo-upload.component';
import { IdentificationResultComponent } from './components/identification-result/identification-result.component';
import { PreviewCardComponent } from './components/preview-card/preview-card.component';
import { IdentificationPreviewSectionComponent } from './components/identification-preview-section/identification-preview-section.component';
import { IdentificationListComponent } from './components/identification-list/identification-list.component';
import { IdentificationPageComponent } from './pages/identification-page/identification-page.component';
import { IdentificationDetailPageComponent } from './pages/identification-detail-page/identification-detail-page.component';
import { IdentificationService } from './services/identification.service';
import { PlantService } from '../plant/services/plant.service';

@NgModule({
  declarations: [
    PhotoUploadComponent,
    IdentificationResultComponent,
    PreviewCardComponent,
    IdentificationPreviewSectionComponent,
    IdentificationListComponent,
    IdentificationPageComponent,
    IdentificationDetailPageComponent,
  ],
  imports: [
    SharedModule,
    IdentificationRoutingModule,
    CarePlanModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  providers: [
    IdentificationService,
    PlantService,
  ],
})
export class IdentificationModule {}
