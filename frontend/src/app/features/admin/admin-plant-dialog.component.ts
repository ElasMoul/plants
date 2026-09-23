import { CommonModule } from "@angular/common";
import { Component, DestroyRef, inject, OnInit } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { MAT_DIALOG_DATA, MatDialogModule } from "@angular/material/dialog";
import { finalize, Subscription } from "rxjs";
import { AdminPlantDetail, AdminService } from "./admin.service";

@Component({
  selector: "app-admin-plant-dialog",
  standalone: true,
  imports: [CommonModule, MatDialogModule],
  templateUrl: "./admin-plant-dialog.component.html",
  styleUrl: "./admin-plant-dialog.component.scss",
})
export class AdminPlantDialogComponent implements OnInit {
  readonly data = inject<{ userId: number; plantId: number; nickname: string }>(
    MAT_DIALOG_DATA,
  );
  private readonly api = inject(AdminService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  detail?: AdminPlantDetail;
  page = 0;
  loading = true;
  error = "";
  photoFailed = false;

  ngOnInit(): void {
    this.load();
  }

  load(page = this.page): void {
    this.request?.unsubscribe();
    this.page = page;
    this.loading = true;
    this.error = "";
    this.request = this.api
      .plantDetail(this.data.userId, this.data.plantId, page)
      .pipe(
        finalize(() => (this.loading = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detail) => (this.detail = detail),
        error: (error) =>
          (this.error =
            error.error?.message ??
            "We couldn’t load this plant. Please try again."),
      });
  }

  label(value: string): string {
    return value.replace(/_/g, " ").toLowerCase();
  }
}
