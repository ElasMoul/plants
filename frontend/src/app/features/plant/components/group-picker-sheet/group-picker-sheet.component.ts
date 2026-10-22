import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { PlantGroup } from '../../models/plant-group.model';
import { PlantGroupService } from '../../services/plant-group.service';

export interface GroupPickerSheetData {
  currentGroupId: number | null;
}

/** Dismisses with the chosen group id, or null for "No group". Dismissing without a choice yields undefined. */
@Component({
    selector: 'app-group-picker-sheet',
    templateUrl: './group-picker-sheet.component.html',
    styleUrls: ['./group-picker-sheet.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class GroupPickerSheetComponent implements OnInit {
  groups: PlantGroup[] = [];
  loading = true;

  constructor(
    private readonly groupService: PlantGroupService,
    private readonly sheetRef: MatBottomSheetRef<GroupPickerSheetComponent, number | null>,
    @Inject(MAT_BOTTOM_SHEET_DATA) readonly data: GroupPickerSheetData,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.groupService.getGroups().subscribe({
      next: (res) => {
        this.groups = res.data?.content ?? [];
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  select(groupId: number | null): void {
    this.sheetRef.dismiss(groupId);
  }
}