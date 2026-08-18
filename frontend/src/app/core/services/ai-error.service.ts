import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiResponse } from '@plantpal/shared-core';

// Surfaces AI-call errors (identification submit, cure advice, craft-plan, chat) consistently —
// a structured 429 from Bucket4j gets an actionable snackbar pointing at Settings, anything else
// shows the real backend message (now that GlobalExceptionHandler returns real status codes/
// messages per T7.1) instead of a generic "something went wrong" toast.
@Injectable({ providedIn: 'root' })
export class AiErrorService {
  constructor(
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  // Returns the display message — useful where the error also needs to render inline (e.g. chat).
  handle(err: HttpErrorResponse): string {
    if (err.status === 429) {
      const message = this.rateLimitMessage(err);
      const ref = this.snackBar.open(message, 'Settings', { duration: 6000 });
      ref.onAction().subscribe(() => this.router.navigate(['/preferences']));
      return message;
    }
    if (err.status === 402) {
      return this.blockedMessage(err);
    }
    if (err.status === 0) {
      return 'Connection problem — check your internet and try again';
    }
    const backendMessage = this.parseBody(err)?.message;
    return backendMessage || 'Something went wrong — please try again';
  }

  // A 402 means a cost ceiling was hit (daily/monthly) — distinct from a 429 rate limit, this
  // isn't fixed by switching AI models, so no "Settings" action here.
  isBlocked(err: HttpErrorResponse): boolean {
    return err.status === 402;
  }

  // The raw block reason from the backend (gateway BlockedResponse.reason), when the body carried
  // one — for the block-state UI to show alongside the friendly title. Returns null when absent.
  blockReason(err: HttpErrorResponse): string | null {
    return this.parseBody(err)?.message ?? null;
  }

  // Same as handle(), but also pops a "Dismiss" snackbar for non-429 errors — for call sites that
  // don't already render the error message inline.
  notify(err: HttpErrorResponse): void {
    const message = this.handle(err);
    if (err.status !== 429) {
      this.snackBar.open(message, 'Dismiss', { duration: 4500 });
    }
  }

  // Normalises err.error into an ApiResponse: it may arrive as a parsed object (JSON responses) or
  // as a raw string (chat streaming uses responseType:'text', so the error body isn't auto-parsed).
  private parseBody(err: HttpErrorResponse): ApiResponse<unknown> | undefined {
    const raw = err.error;
    if (raw == null) return undefined;
    if (typeof raw === 'string') {
      try {
        return JSON.parse(raw) as ApiResponse<unknown>;
      } catch {
        return undefined;
      }
    }
    return raw as ApiResponse<unknown>;
  }

  private rateLimitMessage(err: HttpErrorResponse): string {
    const body = this.parseBody(err);
    const message = body?.message ?? '';
    if (message.toLowerCase().includes('plantnet')) {
      return "Pl@ntNet daily quota reached — try again tomorrow, or switch to a different model in Settings";
    }
    const retryAfterSeconds = body?.retryAfterSeconds;
    const timeText = retryAfterSeconds ? this.formatRetryTime(retryAfterSeconds) : 'a bit';
    return `Rate limit reached — try again in ${timeText}, or switch your AI model in Settings`;
  }

  private blockedMessage(err: HttpErrorResponse): string {
    const body = this.parseBody(err);
    const message = body?.message ?? '';
    if (message.toLowerCase().includes('daily')) {
      return 'Daily AI limit reached — try again tomorrow';
    }
    return 'AI limit reached for this period — try again later';
  }

  private formatRetryTime(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.ceil(seconds / 60);
    return `${minutes}m`;
  }
}
