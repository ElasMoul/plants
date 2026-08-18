import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService, buildAtlasHandoffUrl } from '@plantpal/shared-core';
import { environment } from '../../../../environments/environment';

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

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar,
  ) {
    this.form = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      // "Continue into the Atlas" — on success, redirect to the atlas origin
      // carrying the fresh session (see shared-core's session-handoff).
      openAtlas: [false],
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    const { email, password, openAtlas } = this.form.getRawValue();
    this.authService.login({ email, password }).subscribe({
      next: res => {
        if (openAtlas) {
          // Full navigation to the atlas origin; the session rides the URL
          // fragment (never the query string) and is consumed+scrubbed on boot.
          window.location.assign(
            buildAtlasHandoffUrl(environment.atlasUrl, res.data.token, res.data.user),
          );
          return;
        }
        this.router.navigate(['/garden']);
      },
      error: err => {
        this.loading = false;
        const msg = err.error?.message ?? 'Login failed. Please try again.';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      },
    });
  }
}
