import { CareType } from './reminder.model';

export const CARE_ICONS: Record<CareType, string> = {
  WATERING: 'water_drop',
  LIGHT: 'wb_sunny',
  HUMIDITY: 'opacity',
  TEMPERATURE: 'thermostat',
  FERTILIZING: 'eco',
  REPOTTING: 'yard',
  PRUNING: 'content_cut',
  PEST: 'pest_control',
  SEASONAL: 'calendar_month',
  BEGINNER_TIP: 'lightbulb',
};

export function careIcon(careType: CareType): string {
  return CARE_ICONS[careType];
}
