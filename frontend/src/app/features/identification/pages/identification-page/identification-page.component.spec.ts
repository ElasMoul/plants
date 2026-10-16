import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { IdentificationPageComponent } from './identification-page.component';

describe('IdentificationPageComponent offline queueing', () => {
  const payload = { images: [new File(['x'], 'a.jpg')], organs: ['leaf'] } as any;
  let online: boolean;
  let identification: { analyze: jest.Mock };
  let queue: { enqueue: jest.Mock; start: jest.Mock; uploaded$: Subject<number> };
  let snackBar: { open: jest.Mock };
  let page: IdentificationPageComponent;

  beforeEach(() => {
    online = true;
    jest.spyOn(navigator, 'onLine', 'get').mockImplementation(() => online);
    identification = { analyze: jest.fn() };
    queue = { enqueue: jest.fn().mockResolvedValue({}), start: jest.fn().mockResolvedValue(undefined), uploaded$: new Subject<number>() };
    snackBar = { open: jest.fn() };
    page = new IdentificationPageComponent(
      identification as any, snackBar as any, {} as any, { handle: jest.fn() } as any, queue as any,
    );
    page.photoUpload = { entries: [1] } as any;
  });
  afterEach(() => page.ngOnDestroy());

  it('queues without calling the API while offline', async () => {
    online = false;
    page.onAnalyze(payload);
    await Promise.resolve();
    expect(identification.analyze).not.toHaveBeenCalled();
    expect(queue.enqueue).toHaveBeenCalledWith(payload);
    expect(page.photoUpload!.entries).toEqual([]);
  });

  it('queues when the request fails with a network error', async () => {
    identification.analyze.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    page.onAnalyze(payload);
    await Promise.resolve();
    expect(queue.enqueue).toHaveBeenCalledWith(payload);
    expect(page.submitting).toBe(false);
  });

  it('does not claim success when saving to the device fails', async () => {
    online = false;
    queue.enqueue.mockRejectedValue(new Error('full'));
    page.onAnalyze(payload);
    await new Promise(r => setTimeout(r));
    expect(page.photoUpload!.entries).toEqual([1]);
    expect(snackBar.open).toHaveBeenCalledTimes(1);
  });

  it('starts the queue on init and tracks uploaded scans', () => {
    const trackNew = jest.fn();
    page.listComponent = { trackNew } as any;
    page.ngOnInit();
    queue.uploaded$.next(7);
    expect(queue.start).toHaveBeenCalled();
    expect(trackNew).toHaveBeenCalledWith(7);
  });
});
