import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { ReminderRoutingModule } from './reminder-routing.module';
import { ReminderListComponent } from './reminder-list/reminder-list.component';

@NgModule({
  declarations: [ReminderListComponent],
  imports: [SharedModule, ReminderRoutingModule],
})
export class ReminderModule {}
