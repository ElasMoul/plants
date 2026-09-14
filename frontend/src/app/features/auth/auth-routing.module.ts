import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthCatchAllGuard } from '../../core/guards/auth-catch-all.guard';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';

const routes: Routes = [
  { path: 'login',    component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  // ADR-5: a signed-in-vs-signed-out decision, not a static redirect — see
  // AuthCatchAllGuard for why (kills the old two-hop unknown-deep-link path).
  { path: '**',       canActivate: [AuthCatchAllGuard], children: [] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AuthRoutingModule {}
