import { of, throwError } from 'rxjs';
import { FR } from '../../../../shared/i18n/fr';
import { AR } from '../../../../shared/i18n/ar';
import {
  ReminderPreferences,
  ReminderPreferencesService,
} from '../../services/reminder-preferences.service';
import { ReminderSettingsComponent } from './reminder-settings.component';

describe('ReminderSettingsComponent (PLA-122)', () => {
  const stored: ReminderPreferences = {
    channel: 'PUSH',
    quietHoursStart: null,
    quietHoursEnd: null,
    timeZone: 'UTC',
    emailUnsubscribed: false,
  };

  function build(get: jest.Mock, save: jest.Mock = jest.fn()) {
    const service = { get, save } as unknown as ReminderPreferencesService;
    const component = new ReminderSettingsComponent(service);
    component.ngOnInit();
    return { component, get, save };
  }

  it('seeds the form from the stored preferences', () => {
    const { component } = build(
      jest.fn(() =>
        of({
          ...stored,
          channel: 'BOTH',
          quietHoursStart: '22:00',
          quietHoursEnd: '07:00',
          emailUnsubscribed: true,
        }),
      ),
    );
    expect(component.channel).toBe('BOTH');
    expect(component.quietHoursStart).toBe('22:00');
    expect(component.quietHoursEnd).toBe('07:00');
    expect(component.emailUnsubscribed).toBe(true);
  });

  it('saves the chosen channel, quiet hours and the browser time zone', () => {
    const save = jest.fn(() => of({ ...stored, channel: 'EMAIL' as const }));
    const { component } = build(jest.fn(() => of(stored)), save);
    component.channel = 'EMAIL';
    component.quietHoursStart = '22:00';
    component.quietHoursEnd = '07:00';

    component.save();

    expect(save).toHaveBeenCalledWith({
      channel: 'EMAIL',
      quietHoursStart: '22:00',
      quietHoursEnd: '07:00',
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    });
    expect(component.saved).toBe(true);
  });

  it('sends null quiet hours when both are cleared', () => {
    const save = jest.fn(() => of(stored));
    const { component } = build(jest.fn(() => of(stored)), save);

    component.save();

    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({ quietHoursStart: null, quietHoursEnd: null }),
    );
  });

  it('refuses half-set or equal quiet hours without calling the API', () => {
    const save = jest.fn();
    const { component } = build(jest.fn(() => of(stored)), save);

    component.quietHoursStart = '22:00';
    component.save();
    expect(component.error).toBe('invalid');

    component.quietHoursEnd = '22:00';
    component.save();
    expect(component.error).toBe('invalid');
    expect(save).not.toHaveBeenCalled();
  });

  it('reports a failed save and a failed load', () => {
    const failing = build(
      jest.fn(() => of(stored)),
      jest.fn(() => throwError(() => new Error('boom'))),
    );
    failing.component.save();
    expect(failing.component.error).toBe('failed');
    expect(failing.component.saving).toBe(false);

    const unloadable = build(jest.fn(() => throwError(() => new Error('boom'))));
    expect(unloadable.component.loadError).toBe(true);
  });

  it('has a distinct FR and AR translation for every new string', () => {
    const keys = [
      'Reminders',
      'Send reminders by',
      'reminderChannel.PUSH',
      'reminderChannel.EMAIL',
      'reminderChannel.BOTH',
      'Quiet hours (no reminders)',
      'Save reminder settings',
      'Saving will turn reminder emails back on.',
      'Set both a start and an end time, and make them different.',
    ];
    for (const key of keys) {
      expect(FR[key]).toBeTruthy();
      expect(FR[key]).not.toBe(key);
      expect(AR[key]).toBeTruthy();
      expect(AR[key]).not.toBe(key);
    }
  });
});
