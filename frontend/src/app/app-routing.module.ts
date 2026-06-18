import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

const routes: Routes = [
  // Exact empty path redirect
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  // Guarded feature routes — declared BEFORE the auth catch-all
  // so the prefix '' route below never steals them
  {
    path: 'dashboard',
    loadChildren: () =>
      import('./features/dashboard/dashboard.module').then(m => m.DashboardModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'plants',
    loadChildren: () => import('./features/plant/plant.module').then(m => m.PlantModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'identify',
    loadChildren: () =>
      import('./features/identification/identification.module').then(m => m.IdentificationModule),
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
    loadChildren: () => import('./features/chat/chat.module').then(m => m.ChatModule),
    canActivate: [AuthGuard],
  },
  // Auth catch-all — prefix '' matches /login, /register, and unknown paths.
  // Auth-routing handles login, register, and ** (fallback to plants).
  // Must be last so specific routes above take priority.
  {
    path: '',
    loadChildren: () => import('./features/auth/auth.module').then(m => m.AuthModule),
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
