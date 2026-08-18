import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AnnotationRegion, AnnotationRegionType } from '../../models/identification.model';

const REGION_COLORS: Record<AnnotationRegionType, string> = {
  PLANT:        '#1565C0',
  DISEASE:      '#c62828',
  HEALTHY_AREA: '#2e7d32',
};

const REGION_LABELS: Record<AnnotationRegionType, string> = {
  PLANT:        'Plant',
  DISEASE:      'Disease',
  HEALTHY_AREA: 'Healthy Area',
};

@Component({
    selector: 'app-annotation-list',
    templateUrl: './annotation-list.component.html',
    styleUrls: ['./annotation-list.component.scss'],
    standalone: false
})
export class AnnotationListComponent {
  @Input() regions: AnnotationRegion[] | null = null;
  @Input() species: string | null = null;
  @Input() identificationId!: number;
  @Output() readonly regionSelected = new EventEmitter<number | null>();

  selectedIndex: number | null = null;

  selectRegion(index: number): void {
    if (this.selectedIndex === index) {
      this.selectedIndex = null;
      this.regionSelected.emit(null);
    } else {
      this.selectedIndex = index;
      this.regionSelected.emit(index);
    }
  }

  regionColor(type: AnnotationRegionType): string {
    return REGION_COLORS[type];
  }

  regionTypeLabel(type: AnnotationRegionType): string {
    return REGION_LABELS[type];
  }
}
