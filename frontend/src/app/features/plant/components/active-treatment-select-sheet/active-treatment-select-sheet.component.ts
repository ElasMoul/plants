import { Component, Inject, OnInit } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { TreatmentResponse } from '../../models/treatment.model';
import { IdentificationService } from '../../../identification/services/identification.service';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

export interface ActiveTreatmentSelectSheetData {
  treatments: TreatmentResponse[];
}

interface TreatmentRow {
  treatment: TreatmentResponse;
  photoUrl: string | null;
}

@Component({
  selector: 'app-active-treatment-select-sheet',
  templateUrl: './active-treatment-select-sheet.component.html',
  styleUrls: ['./active-treatment-select-sheet.component.scss'],
})
export class ActiveTreatmentSelectSheetComponent implements OnInit {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  readonly rows: TreatmentRow[];

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly sheetRef: MatBottomSheetRef<ActiveTreatmentSelectSheetComponent, TreatmentResponse>,
    @Inject(MAT_BOTTOM_SHEET_DATA) data: ActiveTreatmentSelectSheetData,
  ) {
    this.rows = data.treatments.map(treatment => ({ treatment, photoUrl: null }));
  }

  ngOnInit(): void {
    this.rows.forEach(row => {
      const identificationId = row.treatment.identificationId;
      if (identificationId == null) return;
      this.identificationService.getById(identificationId).subscribe({
        next: res => { row.photoUrl = res.data.photoUrl; },
        error: () => { /* keep the placeholder thumbnail */ },
      });
    });
  }

  select(treatment: TreatmentResponse): void {
    this.sheetRef.dismiss(treatment);
  }
}
