import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { IdentificationService } from '../../../identification/services/identification.service';
import { IdentificationResponse } from '../../../identification/models/identification.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

export interface PlantScanHistorySheetData {
  plantId: number;
}

@Component({
  selector: 'app-plant-scan-history-sheet',
  templateUrl: './plant-scan-history-sheet.component.html',
  styleUrls: ['./plant-scan-history-sheet.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlantScanHistorySheetComponent implements OnInit {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  scans: IdentificationResponse[] = [];
  loading = true;

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly sheetRef: MatBottomSheetRef<PlantScanHistorySheetComponent, IdentificationResponse>,
    @Inject(MAT_BOTTOM_SHEET_DATA) private readonly data: PlantScanHistorySheetData,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.identificationService.getPlantIdentifications(this.data.plantId, 0, 20).subscribe({
      next: res => {
        // API already returns newest-first (createdAt desc default) — keep as-is for a list,
        // unlike the horizontal timeline strip which reverses to oldest-to-newest.
        this.scans = res.data.content;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  trackByScanId(_index: number, scan: IdentificationResponse): number {
    return scan.id;
  }

  select(scan: IdentificationResponse): void {
    this.sheetRef.dismiss(scan);
  }
}
