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
});
