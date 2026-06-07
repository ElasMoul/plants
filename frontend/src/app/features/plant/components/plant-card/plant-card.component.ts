import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PlantResponse } from '../../models/plant.model';

@Component({
  selector: 'app-plant-card',
  templateUrl: './plant-card.component.html',
  styleUrls: ['./plant-card.component.scss'],
})
export class PlantCardComponent {
  @Input() plant!: PlantResponse;
  @Output() archive = new EventEmitter<number>();
}
