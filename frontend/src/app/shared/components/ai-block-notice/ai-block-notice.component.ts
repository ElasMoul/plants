import { Component, Input } from '@angular/core';

// Dedicated, friendly "AI limit reached" state for a platform ai-gateway block (HTTP 402
// BlockedResponse, or an identification tagged failureReason=AI_LIMIT_REACHED). Platform D023: a
// block must surface as an EXPLICIT state — never a spinner, never a generic error. Rendered inline
// in the chat thread (compact) and as the identification detail terminal state (full).
@Component({
    selector: 'app-ai-block-notice',
    templateUrl: './ai-block-notice.component.html',
    styleUrls: ['./ai-block-notice.component.scss'],
    standalone: false
})
export class AiBlockNoticeComponent {
  // The block reason from the backend when present (e.g. the gateway's BlockedResponse.reason).
  // Shown as a secondary line only when it adds detail beyond the title/guidance.
  @Input() reason: string | null | undefined = null;
  // Compact = inline chat bubble variant; default = full-width detail state screen.
  @Input() compact = false;

  readonly title = 'Daily AI limit reached';
  readonly guidance = 'You’ve used all of today’s AI requests. Please try again tomorrow.';

  get showReason(): boolean {
    const r = this.reason?.trim();
    // Suppress the raw structured tag we set on identifications — it isn't user-facing prose.
    return !!r && r.toUpperCase() !== 'AI_LIMIT_REACHED';
  }
}
