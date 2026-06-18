import { Component, Input } from '@angular/core';
import { CarePlanDto, CareCardDto } from '../../models/identification.model';
import { CareType } from '../../../reminder/models/reminder.model';

@Component({
  selector: 'app-care-plan',
  templateUrl: './care-plan.component.html',
  styleUrls: ['./care-plan.component.scss'],
})
export class CarePlanComponent {
  @Input() carePlan: CarePlanDto | null = null;
  @Input() maxCards: number | null = null;
  @Input() plantId: number | null = null;
  @Input() existingCareTypes: CareType[] = [];

  readonly skeletonCards = [1, 2, 3];

  get visibleCards(): CareCardDto[] {
    if (!this.carePlan) return [];
    return this.maxCards !== null
      ? this.carePlan.careCards.slice(0, this.maxCards)
      : this.carePlan.careCards;
  }
}
