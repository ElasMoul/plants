import { translate } from '../../../shared/i18n/language.service';
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '@plantpal/shared-core';
import { sanitizeReturnUrl } from '../../../core/return-url';
import { SessionMonitorService } from '../../../core/services/session-monitor.service';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss'],
    standalone: false
})
export class LoginComponent {
  form: FormGroup;
  loading = false;
  hidePassword = true;

  // D8: after a signed-out redirect (idle expiry, a protected deep link, or an
  // unknown path), restore the safe destination the visitor was headed to —
  // never an arbitrary one (see return-url.ts's allowlist).
  private readonly returnUrl: string | null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar,
    private readonly sessionMonitorService: SessionMonitorService,
  ) {
    this.returnUrl = sanitizeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
    this.form = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    const { email, password } = this.form.getRawValue();
    this.authService.login({ email, password }).subscribe({
      next: res => {
        // A server-authoritative eviction can return the user to this route
        // immediately after a successful login. Do not leave the submit control
        // disabled in that same SPA visit.
        this.loading = false;
        if (res.data.role === 'ADMIN') {
          void this.router.navigateByUrl('/admin').then(() => this.sessionMonitorService.start('/admin'));
          return;
        }
        const destination = this.returnUrl
            ? this.router.navigateByUrl(this.returnUrl)
            : this.router.navigate(['/garden']);
        // The application shell is not recreated after an eviction. Wait for the
        // safe return navigation before restarting the monitor: its immediate
        // poll may evict synchronously, and must preserve the destination rather
        // than the transient /login route.
        Promise.resolve(destination).then(() => {
          if (res.data.language && document.documentElement.lang !== res.data.language) window.location.reload();
          else this.sessionMonitorService.start(this.returnUrl ?? '/garden');
        });
      },
      error: err => {
        this.loading = false;
        const msg = err.error?.message ?? translate('Login failed. Please try again.');
        this.snackBar.open(translate(msg), translate('Close'), { duration: 4000 });
      },
    });
  }
}
