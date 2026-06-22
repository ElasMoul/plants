import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

const routes: Routes = [
  // Exact empty path redirect
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full',
  },
  // Guarded feature routes — declared BEFORE the auth catch-all
  // so the prefix '' route below never steals them
  {
    path: 'home',
    loadChildren: () =>
      import('./features/dashboard/dashboard.module').then(m => m.DashboardModule),
    canActivate: [AuthGuard],
  },
  // Legacy '/dashboard' URL — DashboardModule is now mounted at 'home' (Home is '', the older
  // health-trends Garden Dashboard from T2.10 moved to 'home/overview'). Redirect keeps existing
  // bookmarks/links working; GardenDashboardComponent itself is unchanged and not deleted.
  {
    path: 'dashboard',
    redirectTo: 'home/overview',
    pathMatch: 'full',
  },
  {
    path: 'garden',
    loadChildren: () => import('./features/species/species.module').then(m => m.SpeciesModule),
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
  // The disease-level Treatment entity's own page (T6.12) — distinct from
  // '/treatment-plans/:id' (ReminderModule, the underlying generic TreatmentPlan page).
  {
    path: 'treatment',
    loadChildren: () => import('./features/treatment/treatment.module').then(m => m.TreatmentModule),
    canActivate: [AuthGuard],
  },
  // ReminderModule is mounted at the root ('') rather than under a 'reminders' prefix —
  // its own routing module defines both 'reminders' and 'treatment-plans/:id' as siblings,
  // so treatment plans get a clean top-level URL instead of /reminders/treatment-plans/:id.
  // canActivate here still guards the whole lazy-loaded subtree, same as every other entry.
  {
    path: '',
    loadChildren: () =>
      import('./features/reminder/reminder.module').then(m => m.ReminderModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'chat',
    loadChildren: () => import('./features/chat/chat.module').then(m => m.ChatModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'preferences',
    loadChildren: () =>
      import('./features/preferences/preferences.module').then(m => m.PreferencesModule),
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
