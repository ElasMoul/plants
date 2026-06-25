import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { SharedModule } from '../../shared/shared.module';
import { ReminderRoutingModule } from './reminder-routing.module';
import { CareLogModule } from './components/care-log/care-log.module';
import { ReminderListComponent } from './reminder-list/reminder-list.component';
import { CreateReminderFormComponent } from './components/create-reminder-form/create-reminder-form.component';
import { CareCalendarComponent } from './components/care-calendar/care-calendar.component';
import { TreatmentPlanDetailComponent } from './pages/treatment-plan-detail/treatment-plan-detail.component';
import { TaskStepComponent } from './pages/task-step/task-step.component';
import { ReminderService } from './services/reminder.service';
import { CareLogService } from './services/care-log.service';
import { TreatmentPlanService } from './services/treatment-plan.service';
import { PlantService } from '../plant/services/plant.service';

@NgModule({
  declarations: [
    ReminderListComponent,
    CreateReminderFormComponent,
    CareCalendarComponent,
    TreatmentPlanDetailComponent,
    TaskStepComponent,
  ],
  imports: [
    SharedModule,
    ReactiveFormsModule,
    ReminderRoutingModule,
    CareLogModule,
    MatDialogModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  providers: [ReminderService, CareLogService, TreatmentPlanService, PlantService],
})
export class ReminderModule {}
