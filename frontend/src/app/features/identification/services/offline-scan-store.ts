import { InjectionToken } from '@angular/core';

export type QueuedScanStatus = 'waiting' | 'uploading' | 'failed';

export interface QueuedScan {
  id: string;
  images: File[];
  organs: string[];
  plantId?: number;
  userContext?: string;
  createdAt: number;
  status: QueuedScanStatus;
  attempts: number;
  lastError?: string;
  /** Epoch ms before which a backed-off retry must not start. */
  nextAttemptAt?: number;
}

/** Persistence port for the offline scan queue. */
export interface OfflineScanStore {
  getAll(): Promise<QueuedScan[]>;
  put(scan: QueuedScan): Promise<void>;
  delete(id: string): Promise<void>;
}

export const OFFLINE_SCAN_STORE = new InjectionToken<OfflineScanStore>('OFFLINE_SCAN_STORE');

const DB_NAME = 'plantpal-offline-scans';
const STORE_NAME = 'scans';

/** Native IndexedDB implementation; stores File blobs so entries survive closing the app. */
export class IndexedDbScanStore implements OfflineScanStore {
  private dbPromise?: Promise<IDBDatabase>;

  getAll(): Promise<QueuedScan[]> {
    return this.run<QueuedScan[]>('readonly', s => s.getAll());
  }

  put(scan: QueuedScan): Promise<void> {
    return this.run<IDBValidKey>('readwrite', s => s.put(scan)).then(() => undefined);
  }

  delete(id: string): Promise<void> {
    return this.run<undefined>('readwrite', s => s.delete(id)).then(() => undefined);
  }

  private open(): Promise<IDBDatabase> {
    if (!this.dbPromise) {
      const opening = new Promise<IDBDatabase>((resolve, reject) => {
        if (typeof indexedDB === 'undefined') {
          reject(new Error('IndexedDB is not available'));
          return;
        }
        const req = indexedDB.open(DB_NAME, 1);
        req.onupgradeneeded = () => req.result.createObjectStore(STORE_NAME, { keyPath: 'id' });
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error ?? new Error('IndexedDB open failed'));
      });
      opening.catch(() => (this.dbPromise = undefined));
      this.dbPromise = opening;
    }
    return this.dbPromise;
  }

  private async run<T>(mode: IDBTransactionMode, op: (s: IDBObjectStore) => IDBRequest): Promise<T> {
    const db = await this.open();
    return new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, mode);
      const req = op(tx.objectStore(STORE_NAME));
      tx.oncomplete = () => resolve(req.result as T);
      tx.onerror = () => reject(tx.error ?? new Error('IndexedDB transaction failed'));
      tx.onabort = () => reject(tx.error ?? new Error('IndexedDB transaction failed'));
    });
  }
}
