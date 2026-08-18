import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CareLogService } from '../../services/care-log.service';
import { CareLogResponse } from '../../models/care-log.model';
import { CareType } from '../../models/reminder.model';
import { careIcon as getCareIcon } from '../../models/care-icon.util';
import { CareLogDetailDialogComponent } from './care-log-detail-dialog.component';

@Component({
    selector: 'app-care-log',
    templateUrl: './care-log.component.html',
    styleUrls: ['./care-log.component.scss'],
    standalone: false
})
export class CareLogComponent implements OnInit, OnDestroy {
  @Input() plantId!: number;

  readonly skeletonRows = [1, 2, 3];

  logs: CareLogResponse[] = [];
  loading = true;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly careLogService: CareLogService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.careLogService.getPlantCareLogs(this.plantId, 0, 20)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.logs = res.data.content;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  careIcon(careType: CareType): string {
    return getCareIcon(careType);
  }

  viewDetails(log: CareLogResponse): void {
    this.dialog.open(CareLogDetailDialogComponent, {
      width: '380px',
      maxWidth: '95vw',
      autoFocus: false,
      data: log,
    });
  }
}
