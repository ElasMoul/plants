import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentificationService } from '../../services/identification.service';
import { IdentificationResponse, SavePreviewEditEvent } from '../../models/identification.model';
import { PlantResponse } from '../../../plant/models/plant.model';

type DetailState = 'loading' | 'pending' | 'ready' | 'failed' | 'not-found';

@Component({
  selector: 'app-identification-detail-page',
  templateUrl: './identification-detail-page.component.html',
  styleUrls: ['./identification-detail-page.component.scss'],
})
export class IdentificationDetailPageComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  result: IdentificationResponse | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadIdentification(id);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSaved(plant: PlantResponse): void {
    this.snackBar.open('Added to your garden!', undefined, { duration: 3000 });
    this.router.navigate(['/plants', plant.id]);
  }

  onEditBeforeSave(event: SavePreviewEditEvent): void {
    if (!this.result) return;
    this.router.navigate(['/plants/new'], {
      queryParams: {
        species:    this.result.scientificName,
        commonName: this.result.commonName,
        nickname:   event.nickname,
        location:   event.location || undefined,
      },
    });
  }

  onDiscard(): void {
    this.router.navigate(['/identify']);
  }

  private loadIdentification(id: number): void {
    this.identificationService.getById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => this.handleResult(res.data),
        error: () => { this.state = 'not-found'; },
      });
  }

  private handleResult(data: IdentificationResponse): void {
    this.result = data;

    if (data.status === 'PENDING') {
      this.state = 'pending';
      this.pollForCompletion(data.id);
      return;
    }
    if (data.status === 'FAILED') {
      this.state = 'failed';
      return;
    }
    if (data.plantId != null) {
      this.router.navigate(['/plants', data.plantId]);
      return;
    }
    this.state = 'ready';
  }

  private pollForCompletion(id: number): void {
    this.identificationService.pollUntilComplete(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => this.handleResult(result),
        error: () => { this.state = 'failed'; },
      });
  }
}
