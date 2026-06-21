import { Component, Inject } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { TreatmentResponse } from '../../models/treatment.model';

export interface ActiveTreatmentSelectSheetData {
  treatments: TreatmentResponse[];
}

@Component({
  selector: 'app-active-treatment-select-sheet',
  templateUrl: './active-treatment-select-sheet.component.html',
  styleUrls: ['./active-treatment-select-sheet.component.scss'],
})
export class ActiveTreatmentSelectSheetComponent {
  readonly treatments: TreatmentResponse[];

  constructor(
    private readonly sheetRef: MatBottomSheetRef<ActiveTreatmentSelectSheetComponent, TreatmentResponse>,
    @Inject(MAT_BOTTOM_SHEET_DATA) data: ActiveTreatmentSelectSheetData,
  ) {
    this.treatments = data.treatments;
  }

  select(treatment: TreatmentResponse): void {
    this.sheetRef.dismiss(treatment);
  }
}
