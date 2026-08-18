import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SpeciesSummaryDto } from '../../models/species.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

@Component({
    selector: 'app-species-card',
    templateUrl: './species-card.component.html',
    styleUrls: ['./species-card.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class SpeciesCardComponent {
  @Input() species!: SpeciesSummaryDto;

  readonly placeholderImage = PLACEHOLDER_IMAGE;

  get hasIssues(): boolean {
    return this.species.healthSummary !== 'All healthy';
  }
}
