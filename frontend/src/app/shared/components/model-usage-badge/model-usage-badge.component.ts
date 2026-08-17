import { Component, Input } from '@angular/core';

const MODEL_LABELS: Record<string, string> = {
  DEEPSEEK: 'GPT-4o',
  DEEPSEEK_R1: 'DeepSeek',
  GITHUB_GPT4O: 'GPT-4o',
  OLLAMA_LLAVA: 'Ollama (local)',
  PLANTNET: 'PlantNet',
};

function modelLabel(model: string | null | undefined): string | null {
  if (!model) return null;
  return MODEL_LABELS[model] ?? model;
}

// Unobtrusive "powered by" caption — used under identification results, disease/cure advice,
// regenerated care cards, treatment descriptions/plans, and species enrichment (T7.2). Renders
// nothing if neither model is known, so it's safe to drop in ahead of the backend fields that
// populate it (T7.1) without ever showing a blank/broken badge.
@Component({
    selector: 'app-model-usage-badge',
    templateUrl: './model-usage-badge.component.html',
    styleUrls: ['./model-usage-badge.component.scss'],
    standalone: false
})
export class ModelUsageBadgeComponent {
  @Input() visionModel: string | null | undefined = null;
  @Input() reasoningModel: string | null | undefined = null;
  @Input() prefix = 'Identified using';

  get visionLabel(): string | null {
    return modelLabel(this.visionModel);
  }

  get reasoningLabel(): string | null {
    return modelLabel(this.reasoningModel);
  }

  get visible(): boolean {
    return !!this.visionLabel || !!this.reasoningLabel;
  }
}
