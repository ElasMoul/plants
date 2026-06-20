import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { PlantResponse } from '../../models/plant.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';
import { ImageLightboxComponent } from '../../../../shared/components/image-lightbox/image-lightbox.component';
import { healthBadgeClass } from '../../../../shared/utils/health-badge.util';

@Component({
  selector: 'app-plant-card',
  templateUrl: './plant-card.component.html',
  styleUrls: ['./plant-card.component.scss'],
})
export class PlantCardComponent {
  @Input() plant!: PlantResponse;
  @Output() archive = new EventEmitter<number>();

  readonly placeholderImage = PLACEHOLDER_IMAGE;
  readonly healthBadgeClass = healthBadgeClass;

  constructor(private readonly dialog: MatDialog) {}

  openLightbox(event: Event): void {
    event.stopPropagation();
    this.dialog.open(ImageLightboxComponent, {
      data: { imageUrl: this.plant.photoUrl || this.placeholderImage, alt: this.plant.nickname },
      panelClass: 'image-lightbox-panel',
      maxWidth: '95vw',
    });
  }
}
