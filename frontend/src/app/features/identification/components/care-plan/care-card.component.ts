import { Component, Input } from '@angular/core';
import { CareCardDto, CareCardType } from '../../models/identification.model';

const CARD_COLORS: Record<CareCardType, string> = {
  WATERING:     '#1565C0',
  LIGHT:        '#F9A825',
  HUMIDITY:     '#0277BD',
  TEMPERATURE:  '#E65100',
  FERTILIZING:  '#558B2F',
  REPOTTING:    '#6D4C41',
  PRUNING:      '#00897B',
  PEST:         '#B71C1C',
  SEASONAL:     '#6A1B9A',
  BEGINNER_TIP: '#2E7D32',
};

@Component({
  selector: 'app-care-card',
  templateUrl: './care-card.component.html',
  styleUrls: ['./care-card.component.scss'],
})
export class CareCardComponent {
  @Input() card!: CareCardDto;

  expanded = false;

  get accentColor(): string {
    return CARD_COLORS[this.card.type] ?? '#616161';
  }

  get accentBg(): string {
    return `${this.accentColor}1A`;
  }

  toggle(): void {
    this.expanded = !this.expanded;
  }
}
