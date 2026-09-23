import { translate } from '../../i18n/language.service';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSelectChange } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserService } from '@plantpal/shared-core';
import { ReasoningModelPreference, VisionModelPreference } from '@plantpal/shared-core';

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
  { value: 'GITHUB_GPT4O',     label: translate('Best'),      intent: 'GPT-4o',          icon: 'smart_toy',  tooltip: translate('Balanced accuracy + care plan quality — ~50 vision calls/day on free tier') },
  { value: 'GITHUB_GPT41',     label: translate('Frontier'),  intent: 'GPT-4.1',         icon: 'auto_awesome', tooltip: translate('Latest GPT vision model — same free-tier quota as GPT-4o') },
  { value: 'DEEPSEEK_FLASH', label: translate('Fast'), intent: 'DeepSeek V4.1 Flash', icon: 'bolt', tooltip: translate('Native DeepSeek API — requires a server API key') },
  { value: 'ANTHROPIC_CLAUDE', label: translate('Specialist'),intent: 'Claude',          icon: 'diamond',    tooltip: translate('Anthropic Claude — strong reasoning about visible health issues') },
  { value: 'OLLAMA_GEMMA3',    label: translate('Offline'),   intent: 'Ollama (gemma3)', icon: 'computer',   tooltip: translate('Fully local — no API quota, needs Ollama running') },
  { value: 'PLANTNET',         label: translate('Balanced'),  intent: 'PlantNet',        icon: 'eco',        tooltip: translate('Plant-only identification, no health/care plan') },
];

const REASONING_OPTIONS: ModelOption<ReasoningModelPreference>[] = [
  { value: 'DEEPSEEK_R1',       label: translate('Best'),      intent: 'DeepSeek-R1',     icon: 'psychology',   tooltip: translate('Deep reasoning for cure advice + descriptions — ~20 calls/hour') },
  { value: 'GITHUB_GPT41_MINI', label: translate('Balanced'),  intent: 'GPT-4.1 mini',    icon: 'bolt',         tooltip: translate('Fast, cheap text generation — same free-tier quota') },
  { value: 'GITHUB_O4_MINI',    label: translate('Frontier'),  intent: 'o4-mini',         icon: 'auto_awesome', tooltip: translate('Latest reasoning-tuned model — same free-tier quota') },
  { value: 'DEEPSEEK_FLASH', label: translate('Fast'), intent: 'DeepSeek V4.1 Flash', icon: 'bolt', tooltip: translate('Native DeepSeek API — requires a server API key') },
  { value: 'ANTHROPIC_CLAUDE',  label: translate('Specialist'),intent: 'Claude',          icon: 'diamond',      tooltip: translate('Anthropic Claude — strong plain-English explanations') },
  { value: 'OLLAMA_GEMMA3',     label: translate('Offline'),   intent: 'Ollama (gemma3)', icon: 'computer',     tooltip: translate('Fully local — no API quota, needs Ollama running') },
];

@Component({
    selector: 'app-model-selector',
    templateUrl: './model-selector.component.html',
    styleUrls: ['./model-selector.component.scss'],
    standalone: false
})
export class ModelSelectorComponent implements OnInit, OnDestroy {
  visionOptions = VISION_OPTIONS;
  reasoningOptions = REASONING_OPTIONS;

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
          this.visionAvailability = { ...res.data.visionModelAvailability };
          this.reasoningAvailability = { ...res.data.reasoningModelAvailability };
          this.visionOptions = VISION_OPTIONS.filter(o => res.data.visionModelVisibility?.[o.value] !== false || o.value === this.selectedVision);
          this.reasoningOptions = REASONING_OPTIONS.filter(o => res.data.reasoningModelVisibility?.[o.value] !== false || o.value === this.selectedReasoning);
          for (const [key, visible] of Object.entries(res.data.visionModelVisibility ?? {})) {
            if (!visible) this.visionAvailability[key as VisionModelPreference] = false;
          }
          for (const [key, visible] of Object.entries(res.data.reasoningModelVisibility ?? {})) {
            if (!visible) this.reasoningAvailability[key as ReasoningModelPreference] = false;
          }
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
          this.snackBar.open(translate("{0} model changed to {1}", [changed === 'vision' ? translate('Vision') : translate('Reasoning'), intent]), undefined, { duration: 2500 });
        },
        error: () => {
          this.selectedVision = previousVision;
          this.selectedReasoning = previousReasoning;
          this.saving = false;
          this.snackBar.open(translate('Could not update model preference.'), translate('Dismiss'), { duration: 4000 });
        },
      });
  }
}
