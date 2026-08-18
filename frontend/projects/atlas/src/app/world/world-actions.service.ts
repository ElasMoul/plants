import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { API_BASE_URL, ApiResponse } from '@plantpal/shared-core';
import { WorldStore } from './world.store';

/** An open in-world form (design-system material). */
export type ActiveForm =
  | { kind: 'add-plant' }
  | { kind: 'add-note'; plantId: number; plantName: string }
  | { kind: 'identify' };

/**
 * Every stake's real behavior (H6). Round-1 families mutate the backend and
 * reload the world; identify-shaped actions open the classic identify flow
 * (species are born from identification — the domain's design); care-loop
 * actions announce their deferral honestly (offline queues everything).
 */
@Injectable({ providedIn: 'root' })
export class WorldActionsService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly store = inject(WorldStore);

  /** The currently open form, if any. */
  readonly activeForm = signal<ActiveForm | null>(null);

  /** Bumped when a mutation succeeded and the world should re-assemble. */
  readonly reloadRequested = signal(0);

  /** Dispatch a stake/action button press for the given node. */
  dispatch(nodeId: string, label: string): void {
    if (this.store.probeOffline()) {
      this.store.say(`Offline: “${label}” is queued. It will run when you are back.`);
      return;
    }
    const l = label.toLowerCase();
    const plant = /^n-plant-(\d+)$/.exec(nodeId);

    if (/add (a |new )?plant/.test(l)) {
      this.activeForm.set({ kind: 'add-plant' });
      return;
    }
    if (l === 'add note' && plant) {
      const name = this.store.nodes().find(n => n.id === nodeId)?.name ?? 'this plant';
      this.activeForm.set({ kind: 'add-note', plantId: Number(plant[1]), plantName: name });
      return;
    }
    if (/try the scan again|retry/.test(l)) {
      this.retryLatestScan();
      return;
    }
    if (/check health/.test(l)) {
      this.healthCheck();
      return;
    }
    if (/identify|scan leaf/.test(l)) {
      this.activeForm.set({ kind: 'identify' });
      return;
    }
    if (/add a species|import a list/.test(l)) {
      // a species is born from an identification — the identify flow lives HERE
      this.store.say('A species is born from an identification — scan the plant.');
      this.activeForm.set({ kind: 'identify' });
      return;
    }
    if (/fetch this region/.test(l)) {
      this.store.say('Fetching…');
      this.reloadRequested.update(v => v + 1);
      return;
    }
    // care-loop family (water/fertilize/reminders/journal…) — honest deferral
    this.store.say(`“${label}” arrives with the care loop — the next round of this atlas.`);
  }

  createPlant(req: { nickname: string; species?: string; location?: string; notes?: string }): void {
    this.http.post<ApiResponse<{ id: number; nickname: string }>>(`${this.base}/plants`, req).subscribe({
      next: res => {
        this.activeForm.set(null);
        this.store.say(`“${res.data.nickname}” planted. A new node takes a free cell — nothing else moves.`);
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The plant could not be saved. Nothing was lost — try again.'),
    });
  }

  addNote(plantId: number, note: string): void {
    this.http.put<ApiResponse<unknown>>(`${this.base}/plants/${plantId}`, { notes: note }).subscribe({
      next: () => {
        this.activeForm.set(null);
        this.store.say('Note recorded. The camera did not move.');
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The note could not be saved — try again.'),
    });
  }

  retryLatestScan(): void {
    const id = this.store.latestFailedScanId();
    if (id == null) {
      this.activeForm.set({ kind: 'identify' });
      this.store.say('No failed scan to retry — start a new one.');
      return;
    }
    this.http.post<ApiResponse<unknown>>(`${this.base}/identifications/${id}/retry`, {}).subscribe({
      next: () => {
        this.store.say('The scan is running again. The answer arrives into this node.');
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The retry could not start — try again.'),
    });
  }

  /** In-atlas identification: multipart analyze → async pipeline → polling. */
  identify(images: File[], organ: string, userContext?: string): void {
    const form = new FormData();
    images.forEach(img => form.append('images', img, img.name));
    form.append('organs', organ);
    if (userContext?.trim()) form.append('userContext', userContext.trim());
    this.http.post<ApiResponse<{ identificationId: number }>>(`${this.base}/identifications/analyze`, form).subscribe({
      next: () => {
        this.activeForm.set(null);
        this.store.say('The scan is running. The answer arrives into the Identification node — nothing moves while it does.');
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The scan could not start. Your photo was not lost — try again.'),
    });
  }

  /** app.health — a real end-to-end call, timed, reported in the node's words. */
  healthCheck(): void {
    const t0 = performance.now();
    this.http.get<ApiResponse<unknown>>(`${this.base}/plants`, { params: { size: 1 } }).subscribe({
      next: () => this.store.say(`Backend answered in ${Math.round(performance.now() - t0)}ms · UP.`),
      error: () => this.store.say('The backend did not answer. The board keeps what it already knows.'),
    });
  }
}
