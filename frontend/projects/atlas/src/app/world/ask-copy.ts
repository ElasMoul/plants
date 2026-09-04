import type { ChatFailure } from './world.dto';

/** Words for one thing — the companion says the same thing wherever it says it. */
export interface AskFailureCopy {
  title: string;
  note: string;
}

/**
 * What the companion says about an ask that did not answer.
 *
 * Two rules the copy keeps: a wait is named only when the body actually carried
 * one (chat's 429 is a plain PlantPalException, so it carries none), and the
 * server's own "ensure Ollama is running locally" is never surfaced — it is
 * wrong for most readers and out of voice.
 */
export function askFailureCopy(failure: ChatFailure): AskFailureCopy {
  switch (failure.kind) {
    case 'rate-limited': {
      const minutes = failure.retryAfterSeconds
        ? Math.max(1, Math.ceil(failure.retryAfterSeconds / 60))
        : 0;
      return {
        title: 'You have asked as much as the hour allows',
        note: minutes
          ? `Ask again in ${minutes} minute${minutes === 1 ? '' : 's'}. Everything already asked is kept.`
          : 'PlantPal did not say how long, so neither will I — it lifts within the hour. Everything already asked is kept.',
      };
    }
    case 'offline':
      return {
        title: 'The question is held here until you are back',
        note: 'Nothing was sent and nothing was lost. Ask again when the connection is.',
      };
    case 'too-long':
      return {
        title: 'That question is longer than I can take',
        note: 'Two thousand characters is the most one question can carry. Shorten it and ask again.',
      };
    case 'not-found':
      return {
        title: 'That plant is not one I can find',
        note: 'It may have been archived. Ask about the garden instead and I will answer generally.',
      };
    case 'blocked':
      return {
        title: 'The AI budget for this period is used up',
        note: 'Nothing is wrong with your garden — only with what I am allowed to spend. It returns next period.',
      };
    default:
      return {
        title: 'The companion cannot reach its thinking right now',
        note: 'Everything already asked is kept, and nothing moved. Ask again in a moment.',
      };
  }
}

/** The one-sentence version the world says out loud; the node itself says more. */
export function askFailureLine(failure: ChatFailure): string {
  switch (failure.kind) {
    case 'rate-limited':
      return 'You have asked as much as the hour allows. The companion says when it lifts.';
    case 'offline':
      return 'Offline: the question is held here. Ask again when you are back.';
    case 'too-long':
      return 'That question is longer than the companion can take.';
    case 'not-found':
      return 'That plant is not one the companion can find.';
    case 'blocked':
      return 'The AI budget for this period is used up.';
    default:
      return 'The companion cannot reach its thinking right now. Nothing moved.';
  }
}
