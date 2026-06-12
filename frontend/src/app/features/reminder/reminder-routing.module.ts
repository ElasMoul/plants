import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ReminderListComponent } from './reminder-list/reminder-list.component';

const routes: Routes = [
  { path: '', component: ReminderListComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ReminderRoutingModule {}
