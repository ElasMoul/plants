import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSelectChange } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserService } from '../../../core/services/user.service';
import { AiModelPreference } from '../../../core/models/user.model';

interface ModelOption {
  value: AiModelPreference;
  label: string;
  icon: string;
  tooltip: string;
}

const MODEL_OPTIONS: ModelOption[] = [
  { value: 'PLANTNET',     label: 'PlantNet',  icon: 'eco',        tooltip: 'Species only — no health analysis' },
  { value: 'DEEPSEEK',     label: 'DeepSeek',  icon: 'psychology', tooltip: '~20 identification calls/hour' },
  { value: 'GITHUB_GPT4O', label: 'GPT-4o',    icon: 'smart_toy',  tooltip: '~50 vision calls/day on free tier' },
  { value: 'OLLAMA_LLAVA', label: 'Ollama',    icon: 'computer',   tooltip: 'Fully local — no API quota' },
];

@Component({
  selector: 'app-model-selector',
  templateUrl: './model-selector.component.html',
  styleUrls: ['./model-selector.component.scss'],
})
export class ModelSelectorComponent implements OnInit, OnDestroy {
  readonly options = MODEL_OPTIONS;
  selected: AiModelPreference = 'DEEPSEEK';
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
        next: res => { this.selected = res.data.aiModelPreference; },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get selectedOption(): ModelOption | undefined {
    return this.options.find(o => o.value === this.selected);
  }

  onChange(event: MatSelectChange): void {
    const value = event.value as AiModelPreference;
    const previous = this.selected;
    this.saving = true;

    this.userService.updatePreferences(value)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.selected = value;
          this.saving = false;
          const label = this.options.find(o => o.value === value)?.label ?? value;
          this.snackBar.open(`Model changed to ${label}`, undefined, { duration: 2500 });
        },
        error: () => {
          this.selected = previous;
          this.saving = false;
          this.snackBar.open('Could not update model preference.', 'Dismiss', { duration: 4000 });
        },
      });
  }
}
