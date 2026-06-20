import { Component, Input } from '@angular/core';
import { PlantResponse } from '../../../plant/models/plant.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';
import { healthBadgeClass } from '../../../../shared/utils/health-badge.util';

@Component({
  selector: 'app-species-plant-row',
  templateUrl: './species-plant-row.component.html',
  styleUrls: ['./species-plant-row.component.scss'],
})
export class SpeciesPlantRowComponent {
  @Input() plant!: PlantResponse;

  readonly placeholderImage = PLACEHOLDER_IMAGE;
  readonly healthBadgeClass = healthBadgeClass;
}
