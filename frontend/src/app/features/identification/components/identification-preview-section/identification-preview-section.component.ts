import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PlantResponse } from '../../../plant/models/plant.model';
import {
  AnnotationRegion,
  CarePlanDto,
  IdentificationResponse,
  SavePreviewEditEvent,
} from '../../models/identification.model';

@Component({
    selector: 'app-identification-preview-section',
    templateUrl: './identification-preview-section.component.html',
    styleUrls: ['./identification-preview-section.component.scss'],
    standalone: false
})
export class IdentificationPreviewSectionComponent {
  @Input() result!: IdentificationResponse;
  @Output() readonly saved = new EventEmitter<PlantResponse>();
  @Output() readonly editBeforeSave = new EventEmitter<SavePreviewEditEvent>();
  @Output() readonly discard = new EventEmitter<void>();

  selectedRegionIndex: number | null = null;
  selectedRegion: AnnotationRegion | null = null;

  onRegionSelected(index: number | null): void {
    this.selectedRegionIndex = index;
    this.selectedRegion = index !== null && this.result.annotationRegions
      ? (this.result.annotationRegions[index] ?? null)
      : null;
  }

  onSaved(plant: PlantResponse): void {
    this.saved.emit(plant);
  }

  onEdit(event: SavePreviewEditEvent): void {
    this.editBeforeSave.emit(event);
  }

  onDiscard(): void {
    this.discard.emit();
  }

  onCarePlanUpdated(plan: CarePlanDto): void {
    this.result.carePlan = plan;
  }
}
