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
  intent: string;
  icon: string;
  tooltip: string;
}

// Labelled by intent (what you'd pick it for), with the underlying model name as the subtitle —
// the option list is the model-picker UI's primary teaching surface for non-experts.
const VISION_OPTIONS: ModelOption<VisionModelPreference>[] = [
  { value: 'GITHUB_GPT4O',     label: 'Best',      intent: 'GPT-4o',          icon: 'smart_toy',  tooltip: 'Balanced accuracy + care plan quality — ~50 vision calls/day on free tier' },
  { value: 'GITHUB_GPT41',     label: 'Frontier',  intent: 'GPT-4.1',         icon: 'auto_awesome', tooltip: 'Latest GPT vision model — same free-tier quota as GPT-4o' },
  { value: 'ANTHROPIC_CLAUDE', label: 'Specialist',intent: 'Claude',          icon: 'diamond',    tooltip: 'Anthropic Claude — strong reasoning about visible health issues' },
  { value: 'OLLAMA_GEMMA3',    label: 'Offline',   intent: 'Ollama (gemma3)', icon: 'computer',   tooltip: 'Fully local — no API quota, needs Ollama running' },
  { value: 'PLANTNET',         label: 'Balanced',  intent: 'PlantNet',        icon: 'eco',        tooltip: 'Plant-only identification, no health/care plan' },
];

const REASONING_OPTIONS: ModelOption<ReasoningModelPreference>[] = [
  { value: 'DEEPSEEK_R1',       label: 'Best',      intent: 'DeepSeek-R1',     icon: 'psychology',   tooltip: 'Deep reasoning for cure advice + descriptions — ~20 calls/hour' },
  { value: 'GITHUB_GPT41_MINI', label: 'Balanced',  intent: 'GPT-4.1 mini',    icon: 'bolt',         tooltip: 'Fast, cheap text generation — same free-tier quota' },
  { value: 'GITHUB_O4_MINI',    label: 'Frontier',  intent: 'o4-mini',         icon: 'auto_awesome', tooltip: 'Latest reasoning-tuned model — same free-tier quota' },
  { value: 'ANTHROPIC_CLAUDE',  label: 'Specialist',intent: 'Claude',          icon: 'diamond',      tooltip: 'Anthropic Claude — strong plain-English explanations' },
  { value: 'OLLAMA_GEMMA3',     label: 'Offline',   intent: 'Ollama (gemma3)', icon: 'computer',     tooltip: 'Fully local — no API quota, needs Ollama running' },
];

@Component({
    selector: 'app-model-selector',
    templateUrl: './model-selector.component.html',
    styleUrls: ['./model-selector.component.scss'],
    standalone: false
})
export class ModelSelectorComponent implements OnInit, OnDestroy {
  readonly visionOptions = VISION_OPTIONS;
  readonly reasoningOptions = REASONING_OPTIONS;

  selectedVision: VisionModelPreference = 'GITHUB_GPT4O';
  selectedReasoning: ReasoningModelPreference = 'DEEPSEEK_R1';
  saving = false;

  private visionAvailability: Partial<Record<VisionModelPreference, boolean>> = {};
  private reasoningAvailability: Partial<Record<ReasoningModelPreference, boolean>> = {};

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
          this.visionAvailability = res.data.visionModelAvailability ?? {};
          this.reasoningAvailability = res.data.reasoningModelAvailability ?? {};
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

  // Missing entry (e.g. cached pre-T8.0 response) defaults to available — only an explicit
  // `false` from the backend disables an option.
  isVisionAvailable(value: VisionModelPreference): boolean {
    return this.visionAvailability[value] !== false;
  }

  isReasoningAvailable(value: ReasoningModelPreference): boolean {
    return this.reasoningAvailability[value] !== false;
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
          const intent = changed === 'vision'
            ? this.visionOptions.find(o => o.value === vision)?.intent
            : this.reasoningOptions.find(o => o.value === reasoning)?.intent;
          this.snackBar.open(`${changed === 'vision' ? 'Vision' : 'Reasoning'} model changed to ${intent}`, undefined, { duration: 2500 });
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
