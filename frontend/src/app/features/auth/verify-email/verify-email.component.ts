import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AuthService, UserService } from '@plantpal/shared-core';

export type VerifyState = 'verifying' | 'verified' | 'invalid';
export type ResendState = 'idle' | 'sending' | 'sent' | 'wait' | 'failed';

// Landing page for the emailed link. The token is POSTed exactly once on load (never GET, so a
// mail scanner prefetching the link cannot spend it); every failure shows the same message.
@Component({
  selector: 'app-verify-email',
  templateUrl: './verify-email.component.html',
  styleUrls: ['../login/login.component.scss'],
  standalone: false,
})
export class VerifyEmailComponent implements OnInit {
  state: VerifyState = 'verifying';
  resendState: ResendState = 'idle';
  readonly signedIn: boolean;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly authService: AuthService,
    private readonly userService: UserService,
  ) {
    this.signedIn = this.authService.isLoggedIn();
  }

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state = 'invalid';
      return;
    }
    this.authService.verifyEmail(token).subscribe({
      next: () => (this.state = 'verified'),
      error: () => (this.state = 'invalid'),
    });
  }

  resend(): void {
    this.resendState = 'sending';
    this.userService.resendVerificationEmail().subscribe({
      next: () => (this.resendState = 'sent'),
      error: (err: HttpErrorResponse) => (this.resendState = err.status === 429 ? 'wait' : 'failed'),
    });
  }
}
