import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { TreatmentRoutingModule } from './treatment-routing.module';
import { TreatmentDetailComponent } from './pages/treatment-detail/treatment-detail.component';
import { TreatmentService } from '../plant/services/treatment.service';
import { PlantService } from '../plant/services/plant.service';
import { IdentificationService } from '../identification/services/identification.service';
import { ReminderService } from '../reminder/services/reminder.service';
import { TreatmentPlanService } from '../reminder/services/treatment-plan.service';

@NgModule({
  declarations: [TreatmentDetailComponent],
  imports: [SharedModule, TreatmentRoutingModule],
  providers: [
    TreatmentService,
    PlantService,
    IdentificationService,
    ReminderService,
    TreatmentPlanService,
  ],
})
export class TreatmentModule {}
