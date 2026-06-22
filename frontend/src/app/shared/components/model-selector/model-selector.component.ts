import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSelectChange } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserService } from '../../../core/services/user.service';
import { ReasoningModelPreference, VisionModelPreference } from '../../../core/models/user.model';

interface ModelOption<T extends string> {
  value: T;
  label: string;
  icon: string;
  tooltip: string;
}

const VISION_OPTIONS: ModelOption<VisionModelPreference>[] = [
  { value: 'GITHUB_GPT4O', label: 'GPT-4o',   icon: 'smart_toy',  tooltip: '~50 vision calls/day on free tier' },
  { value: 'OLLAMA_LLAVA', label: 'Ollama',   icon: 'computer',   tooltip: 'Fully local — no API quota' },
  { value: 'PLANTNET',     label: 'PlantNet', icon: 'eco',        tooltip: 'Plant-only identification, no health/care plan' },
];

const REASONING_OPTIONS: ModelOption<ReasoningModelPreference>[] = [
  { value: 'DEEPSEEK_R1', label: 'DeepSeek', icon: 'psychology', tooltip: '~20 reasoning calls/hour' },
  { value: 'OLLAMA_LLAVA', label: 'Ollama',  icon: 'computer',   tooltip: 'Fully local — no API quota' },
];

@Component({
  selector: 'app-model-selector',
  templateUrl: './model-selector.component.html',
  styleUrls: ['./model-selector.component.scss'],
})
export class ModelSelectorComponent implements OnInit, OnDestroy {
  readonly visionOptions = VISION_OPTIONS;
  readonly reasoningOptions = REASONING_OPTIONS;

  selectedVision: VisionModelPreference = 'GITHUB_GPT4O';
  selectedReasoning: ReasoningModelPreference = 'DEEPSEEK_R1';
  saving = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly userService: UserService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.userService.getPreferences()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.selectedVision = res.data.visionModelPreference;
          this.selectedReasoning = res.data.reasoningModelPreference;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get selectedVisionOption(): ModelOption<VisionModelPreference> | undefined {
    return this.visionOptions.find(o => o.value === this.selectedVision);
  }

  get selectedReasoningOption(): ModelOption<ReasoningModelPreference> | undefined {
    return this.reasoningOptions.find(o => o.value === this.selectedReasoning);
  }

  onVisionChange(event: MatSelectChange): void {
    this.save(event.value as VisionModelPreference, this.selectedReasoning, 'vision');
  }

  onReasoningChange(event: MatSelectChange): void {
    this.save(this.selectedVision, event.value as ReasoningModelPreference, 'reasoning');
  }

  private save(
    vision: VisionModelPreference,
    reasoning: ReasoningModelPreference,
    changed: 'vision' | 'reasoning',
  ): void {
    const previousVision = this.selectedVision;
    const previousReasoning = this.selectedReasoning;
    this.saving = true;

    this.userService.updateModelPreferences(vision, reasoning)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.selectedVision = vision;
          this.selectedReasoning = reasoning;
          this.saving = false;
          const label = changed === 'vision'
            ? this.visionOptions.find(o => o.value === vision)?.label
            : this.reasoningOptions.find(o => o.value === reasoning)?.label;
          this.snackBar.open(`${changed === 'vision' ? 'Vision' : 'Reasoning'} model changed to ${label}`, undefined, { duration: 2500 });
        },
        error: () => {
          this.selectedVision = previousVision;
          this.selectedReasoning = previousReasoning;
          this.saving = false;
          this.snackBar.open('Could not update model preference.', 'Dismiss', { duration: 4000 });
        },
      });
  }
}
