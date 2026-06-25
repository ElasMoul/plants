import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ReminderListComponent } from './reminder-list/reminder-list.component';
import { TreatmentPlanDetailComponent } from './pages/treatment-plan-detail/treatment-plan-detail.component';
import { TaskStepComponent } from './pages/task-step/task-step.component';

// Mounted at the app root ('' — see app-routing.module.ts) so treatment plans get a clean
// top-level /treatment-plans/:id URL instead of being nested under /reminders.
const routes: Routes = [
  { path: 'reminders', component: ReminderListComponent },
  { path: 'treatment-plans/:id', component: TreatmentPlanDetailComponent },
  { path: 'plans/:planId/steps/:stepId', component: TaskStepComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ReminderRoutingModule {}
