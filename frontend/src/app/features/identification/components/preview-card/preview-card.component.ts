import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PlantService } from '../../../plant/services/plant.service';
import { PlantResponse } from '../../../plant/models/plant.model';
import {
  ConfidenceLevel,
  HealthStatus,
  IdentificationResponse,
  SavePreviewEditEvent,
  getConfidenceLevel,
} from '../../models/identification.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

@Component({
  selector: 'app-preview-card',
  templateUrl: './preview-card.component.html',
  styleUrls: ['./preview-card.component.scss'],
})
export class PreviewCardComponent implements OnInit {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  @Input() result!: IdentificationResponse;
  @Input() selectedRegionIndex: number | null = null;
  @Output() readonly saved = new EventEmitter<PlantResponse>();
  @Output() readonly edit = new EventEmitter<SavePreviewEditEvent>();
  @Output() readonly discard = new EventEmitter<void>();

  form!: FormGroup;
  saving = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly plantService: PlantService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      nickname: [
        this.result.commonName || this.result.scientificName || '',
        [Validators.required, Validators.maxLength(255)],
      ],
      location: ['', Validators.maxLength(100)],
    });
  }

  get confidenceLevel(): ConfidenceLevel {
    return getConfidenceLevel(this.result.confidence);
  }

  get confidencePercent(): number {
    return Math.round(this.result.confidence * 100);
  }

  get healthIcon(): string {
    const icons: Record<HealthStatus, string> = {
      HEALTHY:          'check_circle',
      ISSUES_DETECTED:  'warning_amber',
      UNKNOWN:          'help_outline',
    };
    return this.result.healthStatus ? icons[this.result.healthStatus] : '';
  }

  get healthLabel(): string {
    const labels: Record<HealthStatus, string> = {
      HEALTHY:          'Healthy',
      ISSUES_DETECTED:  'Issues detected',
      UNKNOWN:          'Unknown health',
    };
    return this.result.healthStatus ? labels[this.result.healthStatus] : '';
  }

  onSave(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;
    const { nickname, location } = this.form.value as { nickname: string; location: string };
    this.plantService.saveFromIdentification({
      identificationId: this.result.id,
      nickname,
      location: location || undefined,
    }).subscribe({
      next: res => {
        this.saved.emit(res.data);
      },
      error: () => {
        this.snackBar.open('Could not save plant — please try again.', 'Dismiss', { duration: 4000 });
        this.saving = false;
      },
    });
  }

  onEdit(): void {
    const { nickname, location } = this.form.value as { nickname: string; location: string };
    this.edit.emit({ nickname, location });
  }

  onDiscard(): void {
    this.discard.emit();
  }
}
