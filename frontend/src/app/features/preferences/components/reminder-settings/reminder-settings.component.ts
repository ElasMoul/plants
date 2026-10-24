import { Component, OnInit } from '@angular/core';
import {
  ReminderChannel,
  ReminderPreferencesService,
} from '../../services/reminder-preferences.service';

export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

/** Reminder channel (push, email or both) and quiet hours, saved through the notifications API. */
@Component({
    selector: 'app-reminder-settings',
    templateUrl: './reminder-settings.component.html',
    standalone: false
})
export class ReminderSettingsComponent implements OnInit {
  readonly channels: ReminderChannel[] = ['PUSH', 'EMAIL', 'BOTH'];

  channel: ReminderChannel = 'PUSH';
  quietHoursStart = '';
  quietHoursEnd = '';
  emailUnsubscribed = false;
  loadError = false;
  saving = false;
  saved = false;
  error: 'invalid' | 'failed' | null = null;

  constructor(private readonly service: ReminderPreferencesService) {}

  ngOnInit(): void {
    this.service.get().subscribe({
      next: prefs => {
        this.channel = prefs.channel;
        this.quietHoursStart = prefs.quietHoursStart ?? '';
        this.quietHoursEnd = prefs.quietHoursEnd ?? '';
        this.emailUnsubscribed = prefs.emailUnsubscribed;
      },
      error: () => {
        this.loadError = true;
      },
    });
  }

  /** Quiet hours need both ends, and the two must differ. */
  get quietHoursValid(): boolean {
    const hasStart = !!this.quietHoursStart;
    const hasEnd = !!this.quietHoursEnd;
    if (hasStart !== hasEnd) {
      return false;
    }
    return !hasStart || this.quietHoursStart !== this.quietHoursEnd;
  }

  get emailSelected(): boolean {
    return this.channel !== 'PUSH';
  }

  changed(): void {
    this.saved = false;
    this.error = null;
  }

  save(): void {
    if (!this.quietHoursValid) {
      this.error = 'invalid';
      return;
    }
    this.saving = true;
    this.saved = false;
    this.error = null;
    this.service
      .save({
        channel: this.channel,
        quietHoursStart: this.quietHoursStart || null,
        quietHoursEnd: this.quietHoursEnd || null,
        timeZone: browserTimeZone(),
      })
      .subscribe({
        next: prefs => {
          this.emailUnsubscribed = prefs.emailUnsubscribed;
          this.saving = false;
          this.saved = true;
        },
        error: () => {
          this.saving = false;
          this.error = 'failed';
        },
      });
  }
}
