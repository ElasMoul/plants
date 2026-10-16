import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import {
  BACKOFF_BASE_MS,
  MAX_UPLOAD_ATTEMPTS,
  OfflineScanQueueService,
} from './offline-scan-queue.service';
import { OfflineScanStore, QueuedScan } from './offline-scan-store';
import { IdentificationService } from './identification.service';

class MemoryStore implements OfflineScanStore {
  readonly data = new Map<string, QueuedScan>();
  getAll = async () => Array.from(this.data.values());
  put = async (s: QueuedScan) => void this.data.set(s.id, { ...s });
  delete = async (id: string) => void this.data.delete(id);
}

const payload = () => ({
  images: [new File(['x'], 'a.jpg', { type: 'image/jpeg' })],
  organs: ['leaf'],
  plantId: 4,
  userContext: 'brown tips',
});
const httpError = (status: number) => new HttpErrorResponse({ status, error: { message: `err ${status}` } });
const ok = (id = 1) => of({ data: { identificationId: id } });

describe('OfflineScanQueueService', () => {
  let store: MemoryStore;
  let analyze: jest.Mock;
  let online: boolean;
  const make = () =>
    new OfflineScanQueueService({ analyze } as unknown as IdentificationService, store);

  beforeEach(() => {
    jest.useFakeTimers();
    store = new MemoryStore();
    analyze = jest.fn();
    online = false;
    jest.spyOn(navigator, 'onLine', 'get').mockImplementation(() => online);
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('keeps an offline scan as waiting, persists it and makes no request', async () => {
    const svc = make();
    await svc.start();
    await svc.enqueue(payload());

    expect(svc.items).toHaveLength(1);
    expect(svc.items[0].status).toBe('waiting');
    expect(store.data.size).toBe(1);
    expect(analyze).not.toHaveBeenCalled();
    svc.ngOnDestroy();
  });

  it('rejects enqueue when the store cannot save', async () => {
    store.put = async () => {
      throw new Error('quota');
    };
    const svc = make();
    await expect(svc.enqueue(payload())).rejects.toThrow();
    expect(svc.items).toHaveLength(0);
  });

  it('uploads once on reconnect, removes the entry and reports the identification id', async () => {
    analyze.mockReturnValue(ok(42));
    const svc = make();
    const uploaded: number[] = [];
    svc.uploaded$.subscribe(id => uploaded.push(id));
    await svc.start();
    await svc.enqueue(payload());

    online = true;
    window.dispatchEvent(new Event('online'));
    await jest.advanceTimersByTimeAsync(0);

    expect(analyze).toHaveBeenCalledTimes(1);
    expect(analyze).toHaveBeenCalledWith(expect.any(Array), ['leaf'], 4, undefined, 'brown tips');
    expect(uploaded).toEqual([42]);
    expect(svc.items).toHaveLength(0);
    expect(store.data.size).toBe(0);
    svc.ngOnDestroy();
  });

  it('a network error keeps the item waiting without consuming attempts', async () => {
    analyze.mockReturnValue(throwError(() => httpError(0)));
    online = true;
    const svc = make();
    await svc.start();
    await svc.enqueue(payload());
    await svc.drain();

    expect(svc.items[0].status).toBe('waiting');
    expect(svc.items[0].attempts).toBe(0);
    svc.ngOnDestroy();
  });

  it('server errors back off and reach failed at the attempt limit, then stop retrying', async () => {
    analyze.mockReturnValue(throwError(() => httpError(500)));
    online = true;
    const svc = make();
    await svc.start();
    await svc.enqueue(payload());
    await svc.drain();
    expect(svc.items[0].attempts).toBe(1);

    await jest.advanceTimersByTimeAsync(BACKOFF_BASE_MS * 10);
    expect(svc.items[0].status).toBe('failed');
    expect(svc.items[0].attempts).toBe(MAX_UPLOAD_ATTEMPTS);
    expect(svc.items[0].lastError).toBe('err 500');
    expect(analyze).toHaveBeenCalledTimes(MAX_UPLOAD_ATTEMPTS);

    await jest.advanceTimersByTimeAsync(BACKOFF_BASE_MS * 100);
    expect(analyze).toHaveBeenCalledTimes(MAX_UPLOAD_ATTEMPTS);
    svc.ngOnDestroy();
  });

  it('fails immediately on 401 so the user can sign in and retry', async () => {
    analyze.mockReturnValue(throwError(() => httpError(401)));
    online = true;
    const svc = make();
    await svc.start();
    await svc.enqueue(payload());
    await svc.drain();

    expect(svc.items[0].status).toBe('failed');
    expect(analyze).toHaveBeenCalledTimes(1);
    svc.ngOnDestroy();
  });

  it('retry re-uploads a failed item and removes it on success', async () => {
    analyze.mockReturnValueOnce(throwError(() => httpError(401))).mockReturnValue(ok(7));
    online = true;
    const svc = make();
    await svc.start();
    await svc.enqueue(payload());
    await svc.drain();
    expect(svc.items[0].status).toBe('failed');

    await svc.retry(svc.items[0].id);

    expect(analyze).toHaveBeenCalledTimes(2);
    expect(svc.items).toHaveLength(0);
    svc.ngOnDestroy();
  });

  it('discard removes the entry and never triggers a request', async () => {
    const svc = make();
    await svc.start();
    await svc.enqueue(payload());
    await svc.discard(svc.items[0].id);

    online = true;
    await svc.drain();
    expect(svc.items).toHaveLength(0);
    expect(store.data.size).toBe(0);
    expect(analyze).not.toHaveBeenCalled();
    svc.ngOnDestroy();
  });

  it('a fresh instance over the same store restores the queue and drains it when online', async () => {
    const first = make();
    await first.start();
    await first.enqueue(payload());
    first.ngOnDestroy();

    analyze.mockReturnValue(ok(9));
    online = true;
    const reopened = make();
    await reopened.start();

    expect(analyze).toHaveBeenCalledTimes(1);
    const [images, organs] = analyze.mock.calls[0];
    expect(images[0]).toBeInstanceOf(File);
    expect(organs).toEqual(['leaf']);
    expect(reopened.items).toHaveLength(0);
    reopened.ngOnDestroy();
  });

  it('restores an entry left in uploading as waiting', async () => {
    store.data.set('x', { id: 'x', images: [], organs: [], createdAt: 1, status: 'uploading', attempts: 0 });
    const svc = make();
    await svc.start();
    expect(svc.items[0].status).toBe('waiting');
    svc.ngOnDestroy();
  });
});
