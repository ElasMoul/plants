import { MatDatepickerIntl } from '@angular/material/datepicker';
import { translate } from './language.service';

export function localizedDatepicker(): MatDatepickerIntl {
  const labels = new MatDatepickerIntl();
  labels.calendarLabel = translate('Calendar');
  labels.openCalendarLabel = translate('Open calendar');
  labels.closeCalendarLabel = translate('Close calendar');
  labels.prevMonthLabel = translate('Previous month');
  labels.nextMonthLabel = translate('Next month');
  labels.prevYearLabel = translate('Previous year');
  labels.nextYearLabel = translate('Next year');
  labels.prevMultiYearLabel = translate('Previous 24 years');
  labels.nextMultiYearLabel = translate('Next 24 years');
  labels.switchToMonthViewLabel = translate('Choose a date');
  labels.switchToMultiYearViewLabel = translate('Choose month and year');
  return labels;
}
