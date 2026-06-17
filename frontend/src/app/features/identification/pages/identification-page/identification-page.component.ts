import { Component, OnDestroy, ViewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentificationService } from '../../services/identification.service';
import { AnalyzeEmitPayload } from '../../models/identification.model';
import { IdentificationListComponent } from '../../components/identification-list/identification-list.component';
import { IdentificationUploadDialogComponent } from '../../components/identification-upload-dialog/identification-upload-dialog.component';
import { PhotoUploadComponent } from '../../components/photo-upload/photo-upload.component';

@Component({
  selector: 'app-identification-page',
  templateUrl: './identification-page.component.html',
  styleUrls: ['./identification-page.component.scss'],
})
export class IdentificationPageComponent implements OnDestroy {
  @ViewChild(IdentificationListComponent) listComponent?: IdentificationListComponent;
  @ViewChild(PhotoUploadComponent) photoUpload?: PhotoUploadComponent;

  submitting = false;
  hasIdentifications: boolean | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
    private readonly dialog: MatDialog,
  ) {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  openUploadDialog(): void {
    const dialogRef = this.dialog.open(IdentificationUploadDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      autoFocus: false,
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((payload?: AnalyzeEmitPayload) => {
        if (payload) {
          this.onAnalyze(payload);
        }
      });
  }

  triggerAnalyze(): void {
    this.photoUpload?.onAnalyze();
  }

  onAnalyze(payload: AnalyzeEmitPayload): void {
    this.submitting = true;

    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.submitting = false;
          this.snackBar.open(
            "Identification started — we'll update the list when it's ready.",
            undefined,
            { duration: 3000 },
          );
          this.listComponent?.trackNew(res.data.identificationId);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting = false;
          this.snackBar.open(this.mapError(err), 'Dismiss', { duration: 5000 });
        },
      });
  }

  private mapError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Connection problem — check your internet and try again';
    }
    if (err.status === 404) {
      return 'No matching plant species found — try a clearer photo';
    }
    return 'Something went wrong — please try again';
  }
}
