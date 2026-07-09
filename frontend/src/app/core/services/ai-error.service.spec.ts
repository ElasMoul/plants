import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY } from 'rxjs';
import { AiErrorService } from './ai-error.service';

describe('AiErrorService', () => {
  let service: AiErrorService;
  let snackBarOpenSpy: jest.Mock;

  beforeEach(() => {
    snackBarOpenSpy = jest.fn().mockReturnValue({ onAction: () => EMPTY });

    TestBed.configureTestingModule({
      providers: [
        AiErrorService,
        { provide: MatSnackBar, useValue: { open: snackBarOpenSpy } },
        { provide: Router, useValue: { navigate: jest.fn() } },
      ],
    });
    service = TestBed.inject(AiErrorService);
  });

  it('returns rate-limit message on 429 with retryAfterSeconds', () => {
    const err = new HttpErrorResponse({
      status: 429,
      error: { retryAfterSeconds: 90, message: 'rate limited' },
    });
    const msg = service.handle(err);
    expect(msg).toContain('Rate limit reached');
    expect(msg).toContain('2m');
  });

  it('returns PlantNet quota message when body mentions plantnet', () => {
    const err = new HttpErrorResponse({
      status: 429,
      error: { message: 'PlantNet daily quota reached' },
    });
    const msg = service.handle(err);
    expect(msg).toContain("Pl@ntNet daily quota");
  });

  it('returns connection error message on status 0', () => {
    const err = new HttpErrorResponse({ status: 0 });
    const msg = service.handle(err);
    expect(msg).toBe('Connection problem — check your internet and try again');
  });

  it('returns backend message when provided', () => {
    const err = new HttpErrorResponse({
      status: 503,
      error: { message: 'Service temporarily unavailable' },
    });
    const msg = service.handle(err);
    expect(msg).toBe('Service temporarily unavailable');
  });

  it('returns fallback message when no backend message', () => {
    const err = new HttpErrorResponse({ status: 500, error: {} });
    const msg = service.handle(err);
    expect(msg).toBe('Something went wrong — please try again');
  });

  it('opens snackbar on 429', () => {
    const err = new HttpErrorResponse({ status: 429, error: {} });
    service.handle(err);
    expect(snackBarOpenSpy).toHaveBeenCalled();
  });

  it('returns daily limit message on 402 when message mentions daily', () => {
    const err = new HttpErrorResponse({
      status: 402,
      error: { message: 'app plantpal daily ceiling reached' },
    });
    const msg = service.handle(err);
    expect(msg).toBe('Daily AI limit reached — try again tomorrow');
  });

  it('returns generic period-limit message on 402 for a non-daily ceiling', () => {
    const err = new HttpErrorResponse({
      status: 402,
      error: { message: 'global monthly ceiling reached' },
    });
    const msg = service.handle(err);
    expect(msg).toBe('AI limit reached for this period — try again later');
  });

  it('does not open a snackbar via handle() on 402', () => {
    const err = new HttpErrorResponse({ status: 402, error: { message: 'daily ceiling reached' } });
    service.handle(err);
    expect(snackBarOpenSpy).not.toHaveBeenCalled();
  });

  it('isBlocked returns true for 402', () => {
    const err = new HttpErrorResponse({ status: 402, error: {} });
    expect(service.isBlocked(err)).toBe(true);
  });

  it('isBlocked returns false for other statuses', () => {
    const err = new HttpErrorResponse({ status: 429, error: {} });
    expect(service.isBlocked(err)).toBe(false);
  });

  it('blockReason returns the backend message from an object body', () => {
    const err = new HttpErrorResponse({
      status: 402,
      error: { message: 'app plantpal daily ceiling reached' },
    });
    expect(service.blockReason(err)).toBe('app plantpal daily ceiling reached');
  });

  it('blockReason parses a raw text body (chat streaming responseType:text)', () => {
    // Chat streaming sets responseType:'text', so a 402 error body arrives as an unparsed string.
    const err = new HttpErrorResponse({
      status: 402,
      error: JSON.stringify({ success: false, message: 'daily ceiling reached', errorCode: 402 }),
    });
    expect(service.blockReason(err)).toBe('daily ceiling reached');
  });

  it('blockReason returns null when the body carries no message', () => {
    const err = new HttpErrorResponse({ status: 402, error: 'not json' });
    expect(service.blockReason(err)).toBeNull();
  });

  it('handle() still derives the daily block message from a raw text body', () => {
    const err = new HttpErrorResponse({
      status: 402,
      error: JSON.stringify({ message: 'daily ceiling reached' }),
    });
    expect(service.handle(err)).toBe('Daily AI limit reached — try again tomorrow');
  });
});
