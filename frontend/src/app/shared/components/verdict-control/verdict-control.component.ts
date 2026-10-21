import { ChangeDetectorRef, Component, Input, OnChanges } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiResponse } from '@plantpal/shared-core';
import { environment } from '../../../../environments/environment';

export type IdentificationVerdict = 'RIGHT' | 'WRONG' | 'UNSURE';

interface VerdictCandidate {
  scientificName: string;
}

/** "Was this right?" control for an identification result (PLA-102). */
@Component({
  selector: 'app-verdict-control',
  standalone: false,
  template: `
    <section class="verdict-control" data-testid="verdict-control">
      <p class="verdict-label">{{ 'Was this right?' | t }}</p>
      <div class="verdict-options" role="group" [attr.aria-label]="'Was this right?' | t">
        <button
          *ngFor="let option of options"
          mat-stroked-button
          type="button"
          class="verdict-option"
          [class.selected]="verdict === option.value"
          [attr.aria-pressed]="verdict === option.value"
          [attr.data-testid]="'verdict-' + option.value"
          [disabled]="saving"
          (click)="choose(option.value)"
        >{{ option.label | t }}</button>
      </div>
      <div class="verdict-species" *ngIf="verdict === 'WRONG'" data-testid="verdict-species">
        <label for="verdict-species-input">{{ 'What is the correct species? (optional)' | t }}</label>
        <input
          id="verdict-species-input"
          type="text"
          maxlength="255"
          list="verdict-species-options"
          autocomplete="off"
          [(ngModel)]="species"
          (change)="saveSpecies()"
          [disabled]="saving"
        />
        <datalist id="verdict-species-options">
          <option *ngFor="let c of suggestions" [value]="c.scientificName"></option>
        </datalist>
      </div>
      <p class="verdict-status" *ngIf="saved" role="status">{{ 'Thanks for your feedback' | t }}</p>
      <p class="verdict-error" *ngIf="error" role="alert">{{ 'Could not save your answer. Please try again.' | t }}</p>
    </section>
  `,
  styles: [`
    .verdict-control { margin: 16px 0; }
    .verdict-label { font-weight: 500; margin: 0 0 8px; }
    .verdict-options { display: flex; flex-wrap: wrap; gap: 8px; }
    .verdict-option.selected { background: var(--mat-sys-secondary-container, #dfeee0); font-weight: 600; }
    .verdict-species { display: flex; flex-direction: column; gap: 4px; margin-top: 12px; }
    .verdict-species input { padding: 8px; border: 1px solid #999; border-radius: 6px; font: inherit; }
    .verdict-status { margin: 8px 0 0; font-size: 0.9em; }
    .verdict-error { margin: 8px 0 0; font-size: 0.9em; color: #b3261e; }
  `],
})
export class VerdictControlComponent implements OnChanges {
  @Input() identificationId!: number;
  @Input() initialVerdict: IdentificationVerdict | null | undefined = null;
  @Input() initialSpecies: string | null | undefined = null;
  @Input() candidates: VerdictCandidate[] | null | undefined = null;

  readonly options: { value: IdentificationVerdict; label: string }[] = [
    { value: 'RIGHT', label: 'Right' },
    { value: 'WRONG', label: 'Wrong' },
    { value: 'UNSURE', label: 'Not sure' },
  ];

  verdict: IdentificationVerdict | null = null;
  species = '';
  saving = false;
  saved = false;
  error = false;

  constructor(private readonly http: HttpClient, private readonly cdr: ChangeDetectorRef) {}

  get suggestions(): VerdictCandidate[] {
    return this.candidates ?? [];
  }

  ngOnChanges(): void {
    this.verdict = this.initialVerdict ?? null;
    this.species = this.initialSpecies ?? '';
  }

  choose(value: IdentificationVerdict): void {
    this.verdict = value;
    if (value !== 'WRONG') this.species = '';
    this.send();
  }

  saveSpecies(): void {
    if (this.verdict === 'WRONG') this.send();
  }

  private send(): void {
    if (this.verdict == null) return;
    this.saving = true;
    this.saved = false;
    this.error = false;
    const correctSpecies = this.verdict === 'WRONG' ? this.species.trim() || null : null;
    this.http
      .put<ApiResponse<unknown>>(`${environment.apiUrl}/identifications/${this.identificationId}/verdict`, {
        verdict: this.verdict,
        correctSpecies,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.saved = true;
          this.cdr.markForCheck();
        },
        error: () => {
          this.saving = false;
          this.error = true;
          this.cdr.markForCheck();
        },
      });
  }
}
