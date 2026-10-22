import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  JOURNAL_EVENT_LABELS, JOURNAL_EVENT_TYPES, JournalEvent, JournalEventRequest, JournalEventType,
} from '../../models/journal-event.model';

export interface JournalEventDialogData {
  /** Present when editing an existing event. */
  event?: JournalEvent;
}

@Component({
    selector: 'app-journal-event-dialog',
    templateUrl: './journal-event-dialog.component.html',
    standalone: false
})
export class JournalEventDialogComponent {
  readonly types = JOURNAL_EVENT_TYPES;
  readonly labels = JOURNAL_EVENT_LABELS;
  readonly noteMaxLength = 1000;

  type: JournalEventType;
  eventDate: string;
  note: string;

  constructor(
    private readonly ref: MatDialogRef<JournalEventDialogComponent, JournalEventRequest>,
    @Inject(MAT_DIALOG_DATA) public readonly data: JournalEventDialogData,
  ) {
    this.type = data.event?.type ?? 'REPOTTING';
    this.eventDate = data.event?.eventDate ?? JournalEventDialogComponent.today();
    this.note = data.event?.note ?? '';
  }

  get valid(): boolean {
    return !!this.type && /^\d{4}-\d{2}-\d{2}$/.test(this.eventDate);
  }

  save(): void {
    if (!this.valid) return;
    this.ref.close({ type: this.type, eventDate: this.eventDate, note: this.note.trim() || null });
  }

  cancel(): void {
    this.ref.close();
  }

  private static today(): string {
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  }
}
