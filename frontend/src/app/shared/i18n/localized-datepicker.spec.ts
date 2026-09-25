import { localizedDatepicker } from './localized-datepicker';

describe('localizedDatepicker', () => {
  it('fills every navigation label (English source strings by default)', () => {
    const labels = localizedDatepicker();
    expect(labels.calendarLabel).toBe('Calendar');
    expect(labels.openCalendarLabel).toBe('Open calendar');
    expect(labels.prevMonthLabel).toBe('Previous month');
    expect(labels.nextYearLabel).toBe('Next year');
    expect(labels.nextMultiYearLabel).toBe('Next 24 years');
    expect(labels.switchToMultiYearViewLabel).toBe('Choose month and year');
  });
});
