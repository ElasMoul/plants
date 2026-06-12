import { Component, EventEmitter, Input, Output } from '@angular/core';
import {
  ConfidenceLevel,
  IdentificationResponse,
  PlantNetResult,
  SaveAsNewEvent,
  getConfidenceLevel,
} from '../../models/identification.model';

@Component({
  selector: 'app-identification-result',
  templateUrl: './identification-result.component.html',
  styleUrls: ['./identification-result.component.scss'],
})
export class IdentificationResultComponent {
  @Input() result!: IdentificationResponse;
  @Output() readonly saveAsNew = new EventEmitter<SaveAsNewEvent>();
  @Output() readonly updatePlant = new EventEmitter<number>();

  get confidenceLevel(): ConfidenceLevel {
    return getConfidenceLevel(this.result.confidence);
  }

  get confidencePercent(): number {
    return Math.round(this.result.confidence * 100);
  }

  get isLowConfidence(): boolean {
    return this.confidenceLevel === 'LOW';
  }

  getTopResultPercent(item: PlantNetResult): number {
    return Math.round(item.score * 100);
  }

  onSaveAsNew(): void {
    this.saveAsNew.emit({
      scientificName: this.result.scientificName,
      commonName: this.result.commonName,
    });
  }

  onUpdatePlant(): void {
    if (this.result.plantId != null) {
      this.updatePlant.emit(this.result.plantId);
    }
  }
}
