import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { SharedModule } from '../../../../shared/shared.module';
import { OfflineScanQueueService } from '../../services/offline-scan-queue.service';
import { QueuedScan } from '../../services/offline-scan-store';
import { QueuedScansComponent } from './queued-scans.component';

const scan = (over: Partial<QueuedScan>): QueuedScan => ({
  id: 'a',
  images: [new File(['x'], 'a.jpg')],
  organs: ['leaf'],
  createdAt: 1,
  status: 'waiting',
  attempts: 0,
  ...over,
});

describe('QueuedScansComponent', () => {
  const items$ = new BehaviorSubject<QueuedScan[]>([]);
  const queue = { items$, retry: jest.fn(), discard: jest.fn() };

  beforeEach(() => {
    (URL as unknown as Record<string, unknown>)['createObjectURL'] = jest.fn(() => 'blob:thumb');
    (URL as unknown as Record<string, unknown>)['revokeObjectURL'] = jest.fn();
    queue.retry.mockClear();
    queue.discard.mockClear();
    TestBed.configureTestingModule({
      imports: [SharedModule],
      declarations: [QueuedScansComponent],
      providers: [{ provide: OfflineScanQueueService, useValue: queue }],
    });
  });

  it('shows a waiting item with its thumbnail', () => {
    items$.next([scan({})]);
    const fixture = TestBed.createComponent(QueuedScansComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.queued-item')?.getAttribute('data-status')).toBe('waiting');
    expect(el.textContent).toContain('waiting for connection');
    expect(el.querySelector('img.queued-thumb')?.getAttribute('src')).toBe('blob:thumb');
    expect(el.querySelector('.queued-retry')).toBeNull();
  });

  it('offers Retry and Discard on a failed item', () => {
    items$.next([scan({ status: 'failed', lastError: 'boom' })]);
    const fixture = TestBed.createComponent(QueuedScansComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.textContent).toContain('boom');
    (el.querySelector('.queued-retry') as HTMLElement).click();
    (el.querySelector('.queued-discard') as HTMLElement).click();
    expect(queue.retry).toHaveBeenCalledWith('a');
    expect(queue.discard).toHaveBeenCalledWith('a');
  });
});
