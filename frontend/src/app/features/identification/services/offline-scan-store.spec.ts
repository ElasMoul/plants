import { IndexedDbScanStore, QueuedScan } from './offline-scan-store';

/** Minimal in-memory stand-in for the IndexedDB surface IndexedDbScanStore uses (jsdom has none). */
function installFakeIndexedDb(opts: { failOpen?: boolean; failTx?: boolean } = {}) {
  const rows = new Map<string, QueuedScan>();
  const objectStore = () => ({
    getAll: () => ({ result: Array.from(rows.values()) }),
    put: (v: QueuedScan) => (rows.set(v.id, v), { result: v.id }),
    delete: (id: string) => (rows.delete(id), { result: undefined }),
  });
  const db = {
    createObjectStore: jest.fn(),
    transaction: () => {
      const tx: any = { error: new Error('tx failed'), objectStore };
      setTimeout(() => (opts.failTx ? tx.onerror?.() : tx.oncomplete?.()));
      return tx;
    },
  };
  const open = jest.fn(() => {
    const req: any = { result: db, error: new Error('open failed') };
    setTimeout(() => {
      if (opts.failOpen) {
        req.onerror?.();
        return;
      }
      req.onupgradeneeded?.();
      req.onsuccess?.();
    });
    return req;
  });
  (globalThis as any).indexedDB = { open };
  return { open, db, rows };
}

const scan = (id: string): QueuedScan => ({
  id, images: [], organs: [], createdAt: 1, status: 'waiting', attempts: 0,
});

describe('IndexedDbScanStore', () => {
  afterEach(() => delete (globalThis as any).indexedDB);

  it('puts, lists and deletes entries, opening the database once', async () => {
    const fake = installFakeIndexedDb();
    const store = new IndexedDbScanStore();
    await store.put(scan('a'));
    await store.put(scan('b'));
    expect((await store.getAll()).map(s => s.id)).toEqual(['a', 'b']);
    await store.delete('a');
    expect((await store.getAll()).map(s => s.id)).toEqual(['b']);
    expect(fake.open).toHaveBeenCalledTimes(1);
    expect(fake.db.createObjectStore).toHaveBeenCalledWith('scans', { keyPath: 'id' });
  });

  it('rejects when IndexedDB is unavailable', async () => {
    await expect(new IndexedDbScanStore().getAll()).rejects.toThrow('IndexedDB is not available');
  });

  it('rejects when opening fails and retries opening on the next call', async () => {
    const fake = installFakeIndexedDb({ failOpen: true });
    const store = new IndexedDbScanStore();
    await expect(store.getAll()).rejects.toThrow('open failed');
    await expect(store.getAll()).rejects.toThrow('open failed');
    expect(fake.open).toHaveBeenCalledTimes(2);
  });

  it('rejects when the transaction errors', async () => {
    installFakeIndexedDb({ failTx: true });
    await expect(new IndexedDbScanStore().put(scan('a'))).rejects.toThrow('tx failed');
  });
});
