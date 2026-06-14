import { Component, Input } from '@angular/core';
import { CarePlanDto, CareCardDto } from '../../models/identification.model';

@Component({
  selector: 'app-care-plan',
  templateUrl: './care-plan.component.html',
  styleUrls: ['./care-plan.component.scss'],
})
export class CarePlanComponent {
  @Input() carePlan: CarePlanDto | null = null;
  @Input() maxCards: number | null = null;

  readonly skeletonCards = [1, 2, 3];

  get visibleCards(): CareCardDto[] {
    if (!this.carePlan) return [];
    return this.maxCards !== null
      ? this.carePlan.careCards.slice(0, this.maxCards)
      : this.carePlan.careCards;
  }
}
