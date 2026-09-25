import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of, throwError } from 'rxjs';
import { AiErrorService } from '../../../core/services/ai-error.service';
import { BatchScanService } from './batch-scan.service';
import { IdentificationService } from './identification.service';
import { IdentificationResponse } from '../models/identification.model';

type Poll = Subject<IdentificationResponse>;

function scan(overrides: Partial<IdentificationResponse>): IdentificationResponse {
  return {
    id: 1,
    status: 'COMPLETED',
    annotationStatus: 'COMPLETED',
    candidateStatus: 'COMPLETED',
    ...overrides,
  } as IdentificationResponse;
}

describe('BatchScanService', () => {
  let polls: Poll[];
  let identification: { analyze: jest.Mock; pollUntilComplete: jest.Mock };
  let snackBar: { open: jest.Mock };
  let onAction: Subject<void>;
  let router: { navigate: jest.Mock };
  let aiError: { handle: jest.Mock };
  let service: BatchScanService;

  const file = (name: string) => ({ file: new File(['x'], name), preview: `data:${name}` });

  beforeEach(() => {
    polls = [];
    let nextId = 100;
    identification = {
      analyze: jest.fn(() => of({ data: { identificationId: nextId++ } })),
      pollUntilComplete: jest.fn(() => {
        const poll: Poll = new Subject();
        polls.push(poll);
        return poll;
      }),
    };
    onAction = new Subject<void>();
    snackBar = { open: jest.fn(() => ({ onAction: () => onAction })) };
    router = { navigate: jest.fn() };
    aiError = { handle: jest.fn(() => 'Rate limited — try again in 1 minute') };
    service = new BatchScanService(
      identification as unknown as IdentificationService,
      aiError as unknown as AiErrorService,
      snackBar as unknown as MatSnackBar,
      router as unknown as Router,
    );
  });

  it('fires every analyze call immediately and marks items SCANNING', () => {
    service.start([file('a.jpg'), file('b.jpg')], 'yellow leaves');

    expect(identification.analyze).toHaveBeenCalledTimes(2);
    expect(identification.analyze.mock.calls[0][4]).toBe('yellow leaves');
    expect(service.items.map(i => i.status)).toEqual(['SCANNING', 'SCANNING']);
    expect(service.running).toBe(true);
    expect(service.done).toBe(false);
  });

  it('marks items DONE and announces the batch once when every scan completes', () => {
    service.start([file('a.jpg'), file('b.jpg')]);

    polls[0].next(scan({}));
    polls[0].complete();
    expect(snackBar.open).not.toHaveBeenCalled();
    polls[1].next(scan({}));
    polls[1].complete();

    expect(service.items.map(i => i.status)).toEqual(['DONE', 'DONE']);
    expect(service.done).toBe(true);
    expect(service.running).toBe(false);
    expect(snackBar.open).toHaveBeenCalledTimes(1);
    expect(snackBar.open.mock.calls[0][0]).toContain('2 plants added');
  });

  it('a scan the backend marks FAILED is a failed item, not an added plant', () => {
    service.start([file('a.jpg')]);

    polls[0].next(scan({ status: 'FAILED' } as Partial<IdentificationResponse>));
    polls[0].complete();

    expect(service.items[0].status).toBe('FAILED');
    expect(service.hasFailures).toBe(true);
    expect(snackBar.open.mock.calls[0][0]).toContain('0 added, 1 failed');
  });

  it('announces the batch once even while enrichment keeps polling', () => {
    service.start([file('a.jpg')]);

    // Core COMPLETED but annotation/candidates still PENDING: the poll keeps emitting every 3s.
    polls[0].next(scan({ annotationStatus: 'PENDING' } as Partial<IdentificationResponse>));
    polls[0].next(scan({ annotationStatus: 'PENDING' } as Partial<IdentificationResponse>));
    polls[0].next(scan({}));
    polls[0].complete();

    expect(service.items[0].status).toBe('DONE');
    expect(snackBar.open).toHaveBeenCalledTimes(1);
  });

  it('an analyze error fails that item with the AI error message', () => {
    identification.analyze.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 429 })),
    );

    service.start([file('a.jpg')]);

    expect(service.items[0].status).toBe('FAILED');
    expect(service.items[0].errorMessage).toBe('Rate limited — try again in 1 minute');
    expect(snackBar.open.mock.calls[0][0]).toContain('0 added, 1 failed');
  });

  it('a polling error (timeout) fails that item', () => {
    service.start([file('a.jpg')]);

    polls[0].error(new Error('Timeout has occurred'));

    expect(service.items[0].status).toBe('FAILED');
    expect(service.items[0].errorMessage).toBeTruthy();
  });

  it('retryFailed re-runs only the failed items', () => {
    service.start([file('a.jpg'), file('b.jpg')]);
    polls[0].next(scan({}));
    polls[0].complete();
    polls[1].error(new Error('boom'));
    identification.analyze.mockClear();

    service.retryFailed();

    expect(identification.analyze).toHaveBeenCalledTimes(1);
    expect(service.items.map(i => i.status)).toEqual(['DONE', 'SCANNING']);
    expect(service.items[1].errorMessage).toBeUndefined();
    expect(service.running).toBe(true);
  });

  it('the snackbar action opens the identify list, and reset clears the batch', () => {
    service.start([file('a.jpg')]);
    polls[0].next(scan({}));
    polls[0].complete();

    onAction.next();
    expect(router.navigate).toHaveBeenCalledWith(['/identify']);

    service.reset();
    expect(service.items).toEqual([]);
    expect(service.running).toBe(false);
    expect(service.done).toBe(false);
  });
});
