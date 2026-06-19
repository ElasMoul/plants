import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subject, Subscription, interval, of } from 'rxjs';
import { catchError, map, switchMap, takeUntil } from 'rxjs/operators';
import { IdentificationService } from '../../services/identification.service';
import { aiModelLabel, IdentificationResponse } from '../../models/identification.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

const PAGE_SIZE = 10;
const POLL_INTERVAL_MS = 3000;

@Component({
  selector: 'app-identification-list',
  templateUrl: './identification-list.component.html',
  styleUrls: ['./identification-list.component.scss'],
})
export class IdentificationListComponent implements OnInit, OnDestroy {
  @Output() readonly hasItemsChange = new EventEmitter<boolean>();

  readonly placeholderImage = PLACEHOLDER_IMAGE;
  items: IdentificationResponse[] = [];
  loading = true;
  loadError = false;

  private pollSub: Subscription | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.fetchPage().subscribe(() => this.ensurePolling());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackNew(id: number): void {
    if (!this.items.some(item => item.id === id)) {
      this.items = [this.placeholder(id), ...this.items];
    }
    this.fetchPage().subscribe(() => this.ensurePolling());
  }

  onRowClick(item: IdentificationResponse): void {
    this.router.navigate(['/identify', item.id]);
  }

  trackById(_index: number, item: IdentificationResponse): number {
    return item.id;
  }

  modelLabel(item: IdentificationResponse): string | null {
    return aiModelLabel(item.aiModelUsed);
  }

  private fetchPage(): Observable<void> {
    return this.identificationService.getUserIdentifications(0, PAGE_SIZE).pipe(
      takeUntil(this.destroy$),
      map(res => {
        this.items = res.data.content;
        this.loading = false;
        this.loadError = false;
        this.hasItemsChange.emit(this.items.length > 0);
      }),
      catchError(() => {
        this.loading = false;
        this.loadError = true;
        return of(undefined);
      }),
    );
  }

  private ensurePolling(): void {
    if (this.pollSub && !this.pollSub.closed) return;
    if (!this.hasPending()) return;

    this.pollSub = interval(POLL_INTERVAL_MS).pipe(
      takeUntil(this.destroy$),
      switchMap(() => this.fetchPage()),
    ).subscribe(() => {
      if (!this.hasPending()) {
        this.pollSub?.unsubscribe();
      }
    });
  }

  private hasPending(): boolean {
    return this.items.some(item => item.status === 'PENDING');
  }

  private placeholder(id: number): IdentificationResponse {
    return {
      id,
      plantId: null,
      scientificName: '',
      commonName: '',
      confidence: 0,
      healthStatus: null,
      healthNotes: null,
      status: 'PENDING',
      topResults: [],
      photoUrl: '',
      carePlan: null,
      annotationRegions: null,
      createdAt: new Date().toISOString(),
    };
  }
}
