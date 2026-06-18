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
import { ReminderService } from './services/reminder.service';
import { CareLogService } from './services/care-log.service';
import { PlantService } from '../plant/services/plant.service';

@NgModule({
  declarations: [
    ReminderListComponent,
    CreateReminderFormComponent,
    CareCalendarComponent,
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
  providers: [ReminderService, CareLogService, PlantService],
})
export class ReminderModule {}
