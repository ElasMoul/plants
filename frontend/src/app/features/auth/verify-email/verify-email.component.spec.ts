import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthService, UserService } from '@plantpal/shared-core';
import { FR } from '../../../shared/i18n/fr';
import { AR } from '../../../shared/i18n/ar';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent (PLA-26)', () => {
  function build(token: string | null, opts: { signedIn?: boolean; verify?: unknown; resend?: unknown } = {}) {
    const authService = {
      isLoggedIn: jest.fn(() => opts.signedIn ?? false),
      verifyEmail: jest.fn(() => opts.verify ?? of({ success: true })),
    };
    const userService = { resendVerificationEmail: jest.fn(() => opts.resend ?? of({ success: true })) };
    const route = { snapshot: { queryParamMap: { get: jest.fn(() => token) } } } as unknown as ActivatedRoute;
    const component = new VerifyEmailComponent(
      route,
      authService as unknown as AuthService,
      userService as unknown as UserService,
    );
    component.ngOnInit();
    return { component, authService, userService };
  }

  it('posts the token once and shows success', () => {
    const { component, authService } = build('abc123');
    expect(authService.verifyEmail).toHaveBeenCalledTimes(1);
    expect(authService.verifyEmail).toHaveBeenCalledWith('abc123');
    expect(component.state).toBe('verified');
  });

  it('shows the same invalid state for a refused token', () => {
    const { component } = build('bad', {
      verify: throwError(() => new HttpErrorResponse({ status: 400 })),
    });
    expect(component.state).toBe('invalid');
  });

  it('is invalid without calling the API when the link carries no token', () => {
    const { component, authService } = build(null);
    expect(authService.verifyEmail).not.toHaveBeenCalled();
    expect(component.state).toBe('invalid');
  });

  it('resend reports sent, then a 429 as a wait message, other errors as failed', () => {
    const sent = build('bad', { signedIn: true, verify: throwError(() => new HttpErrorResponse({ status: 400 })) });
    sent.component.resend();
    expect(sent.userService.resendVerificationEmail).toHaveBeenCalled();
    expect(sent.component.resendState).toBe('sent');

    const wait = build('bad', {
      signedIn: true,
      verify: throwError(() => new HttpErrorResponse({ status: 400 })),
      resend: throwError(() => new HttpErrorResponse({ status: 429 })),
    });
    wait.component.resend();
    expect(wait.component.resendState).toBe('wait');

    const failed = build('bad', {
      signedIn: true,
      verify: throwError(() => new HttpErrorResponse({ status: 400 })),
      resend: throwError(() => new HttpErrorResponse({ status: 500 })),
    });
    failed.component.resend();
    expect(failed.component.resendState).toBe('failed');
  });

  it('has French and Arabic text for every string on the page and the profile', () => {
    const keys = [
      'Verify your email', 'Verifying your email…', 'Your email address is verified. Thank you!',
      'This verification link is invalid or has expired.', 'Send me a new link',
      'Sign in to request a new link.', 'Go to my garden', 'Email verification',
      'Your email address is verified.',
      'Your email address is not verified yet. Check your inbox, or request a new link. Until then you cannot start new identifications or chats.',
      'Resend verification email', 'Sending…', 'Verification email sent. Check your inbox.',
      'Please wait a moment before requesting another email.',
      'Could not send the verification email. Please try again.',
      'Verify your email address to use this feature. Check your inbox, or resend the link in Settings.',
    ];
    for (const key of keys) {
      expect(FR[key]?.trim()).toBeTruthy();
      expect(AR[key]?.trim()).toBeTruthy();
      expect(FR[key]).not.toBe(key);
    }
  });
});
