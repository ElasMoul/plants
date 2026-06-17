import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PlantResponse } from '../../models/plant.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

@Component({
  selector: 'app-plant-card',
  templateUrl: './plant-card.component.html',
  styleUrls: ['./plant-card.component.scss'],
})
export class PlantCardComponent {
  @Input() plant!: PlantResponse;
  @Output() archive = new EventEmitter<number>();

  readonly placeholderImage = PLACEHOLDER_IMAGE;
}
