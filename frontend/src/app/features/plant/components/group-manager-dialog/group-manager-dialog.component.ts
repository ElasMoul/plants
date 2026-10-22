import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { translate } from '../../../../shared/i18n/language.service';
import { GroupCareAction, PlantGroup } from '../../models/plant-group.model';
import { PlantGroupService } from '../../services/plant-group.service';

@Component({
    selector: 'app-group-manager-dialog',
    templateUrl: './group-manager-dialog.component.html',
    styleUrls: ['./group-manager-dialog.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class GroupManagerDialogComponent implements OnInit {
  groups: PlantGroup[] = [];
  loading = true;
  newName = '';
  editingId: number | null = null;
  editName = '';
  /** True once a group was created, renamed, deleted or cared for, so the list must refresh. */
  changed = false;

  constructor(
    private readonly groupService: PlantGroupService,
    private readonly snackBar: MatSnackBar,
    private readonly dialogRef: MatDialogRef<GroupManagerDialogComponent, boolean>,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.groupService.getGroups().subscribe({
      next: (res) => {
        this.groups = res.data?.content ?? [];
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => this.fail('Could not load groups.'),
    });
  }

  create(): void {
    const name = this.newName.trim();
    if (!name) return;
    this.groupService.createGroup(name).subscribe({
      next: () => {
        this.newName = '';
        this.changed = true;
        this.load();
      },
      error: () => this.fail('Could not save the group.'),
    });
  }

  startEdit(group: PlantGroup): void {
    this.editingId = group.id;
    this.editName = group.name;
  }

  cancelEdit(): void {
    this.editingId = null;
  }

  saveEdit(group: PlantGroup): void {
    const name = this.editName.trim();
    if (!name) return;
    this.groupService.renameGroup(group.id, name).subscribe({
      next: () => {
        this.editingId = null;
        this.changed = true;
        this.load();
      },
      error: () => this.fail('Could not save the group.'),
    });
  }

  remove(group: PlantGroup): void {
    if (!window.confirm(translate('Delete this group? Its plants are kept.'))) return;
    this.groupService.deleteGroup(group.id).subscribe({
      next: () => {
        this.changed = true;
        this.load();
      },
      error: () => this.fail('Could not delete the group.'),
    });
  }

  care(group: PlantGroup, action: GroupCareAction): void {
    this.groupService.logCare(group.id, action).subscribe({
      next: (res) => {
        const count = res.data?.length ?? 0;
        this.changed = true;
        this.snackBar.open(translate('Care logged for {0} plant(s).', [count]), undefined, { duration: 3000 });
        this.cdr.markForCheck();
      },
      error: () => this.fail('Could not log care for the group.'),
    });
  }

  close(): void {
    this.dialogRef.close(this.changed);
  }

  trackById(_index: number, group: PlantGroup): number {
    return group.id;
  }

  private fail(message: string): void {
    this.loading = false;
    this.snackBar.open(translate(message), translate('Dismiss'), { duration: 4000 });
    this.cdr.markForCheck();
  }
}