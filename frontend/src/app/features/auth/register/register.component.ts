import { Component } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const pw      = group.get('password')?.value as string;
  const confirm = group.get('confirmPassword')?.value as string;
  return pw && confirm && pw !== confirm ? { passwordsMismatch: true } : null;
}

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
})
export class RegisterComponent {
  form: FormGroup;
  loading = false;
  hidePassword = true;
  hideConfirm  = true;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
  ) {
    this.form = this.fb.group(
      {
        firstName:       ['', [Validators.required, Validators.maxLength(100)]],
        lastName:        ['', [Validators.required, Validators.maxLength(100)]],
        email:           ['', [Validators.required, Validators.email]],
        password:        ['', [Validators.required, Validators.minLength(8)]],
        confirmPassword: ['', Validators.required],
      },
      { validators: passwordsMatch },
    );
  }

  get mismatch(): boolean {
    return (
      this.form.hasError('passwordsMismatch') &&
      (this.form.get('confirmPassword')?.touched ?? false)
    );
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    // Strip confirmPassword — backend doesn't expect it
    const { confirmPassword: _ignored, ...payload } = this.form.getRawValue() as {
      confirmPassword: string;
      firstName: string;
      lastName: string;
      email: string;
      password: string;
    };

    this.authService.register(payload).subscribe({
      next: () => this.router.navigate(['/plants']),
      error: err => {
        this.loading = false;
        const msg = (err.error?.message as string | undefined) ?? 'Registration failed. Please try again.';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      },
    });
  }
}
