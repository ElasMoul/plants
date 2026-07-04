import { Component, Inject } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from '@angular/material/bottom-sheet';

export type PlantAction = 'updatePhoto' | 'archive' | 'scan' | 'chat';

export interface PlantActionsSheetData {
  nickname: string;
}

@Component({
  selector: 'app-plant-actions-sheet',
  templateUrl: './plant-actions-sheet.component.html',
  styleUrls: ['./plant-actions-sheet.component.scss'],
})
export class PlantActionsSheetComponent {
  constructor(
    private readonly sheetRef: MatBottomSheetRef<PlantActionsSheetComponent, PlantAction>,
    @Inject(MAT_BOTTOM_SHEET_DATA) readonly data: PlantActionsSheetData,
  ) {}

  select(action: PlantAction): void {
    this.sheetRef.dismiss(action);
  }
}
