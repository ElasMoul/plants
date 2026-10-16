import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { OfflineScanQueueService } from '../../services/offline-scan-queue.service';
import { QueuedScan } from '../../services/offline-scan-store';

@Component({
  selector: 'app-queued-scans',
  templateUrl: './queued-scans.component.html',
  styleUrls: ['./queued-scans.component.scss'],
  standalone: false,
})
export class QueuedScansComponent implements OnInit, OnDestroy {
  items: QueuedScan[] = [];

  private readonly thumbs = new Map<string, string>();
  private sub?: Subscription;

  constructor(
    private readonly queue: OfflineScanQueueService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.sub = this.queue.items$.subscribe(items => {
      this.items = items;
      this.syncThumbs();
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.thumbs.forEach(url => URL.revokeObjectURL(url));
    this.thumbs.clear();
  }

  thumb(scan: QueuedScan): string | null {
    return this.thumbs.get(scan.id) ?? null;
  }

  retry(scan: QueuedScan): void {
    void this.queue.retry(scan.id);
  }

  discard(scan: QueuedScan): void {
    void this.queue.discard(scan.id);
  }

  trackById(_: number, scan: QueuedScan): string {
    return scan.id;
  }

  private syncThumbs(): void {
    const live = new Set(this.items.map(s => s.id));
    for (const [id, url] of Array.from(this.thumbs)) {
      if (!live.has(id)) {
        URL.revokeObjectURL(url);
        this.thumbs.delete(id);
      }
    }
    for (const scan of this.items) {
      if (!this.thumbs.has(scan.id) && scan.images[0]) {
        this.thumbs.set(scan.id, URL.createObjectURL(scan.images[0]));
      }
    }
  }
}
