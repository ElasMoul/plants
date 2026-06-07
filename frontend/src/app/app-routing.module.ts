import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'ai-test',
    pathMatch: 'full',
  },
  {
    path: 'login',
    loadChildren: () =>
      import('./features/auth/auth.module').then(m => m.AuthModule),
  },
  {
    path: 'register',
    loadChildren: () =>
      import('./features/auth/auth.module').then(m => m.AuthModule),
  },
  {
    path: 'plants',
    loadChildren: () =>
      import('./features/plant/plant.module').then(m => m.PlantModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'identify',
    loadChildren: () =>
      import('./features/identification/identification.module').then(
        m => m.IdentificationModule,
      ),
    canActivate: [AuthGuard],
  },
  {
    path: 'reminders',
    loadChildren: () =>
      import('./features/reminder/reminder.module').then(m => m.ReminderModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'chat',
    loadChildren: () =>
      import('./features/chat/chat.module').then(m => m.ChatModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'ai-test',
    loadChildren: () =>
      import('./features/ai-test/ai-test.module').then(m => m.AiTestModule),
  },
  {
    path: '**',
    redirectTo: 'ai-test',
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
