import { Injectable } from '@angular/core';

/**
 * First-run funnel events. PROVISIONAL: the PLA-78 event definitions were not available in the
 * repository when PLA-117 was built, so these names are placeholders kept in this single place.
 * Replace the values here with the PLA-78 names; nothing else references the literals.
 */
export const FIRST_RUN_EVENTS = {
  introShown: 'first_run_intro_shown',
  introSkipped: 'first_run_intro_skipped',
  introFinished: 'first_run_intro_finished',
  tipsViewed: 'first_run_photo_tips_viewed',
  cameraExplainerShown: 'first_run_camera_explainer_shown',
  cameraPermissionRefused: 'first_run_camera_permission_refused',
  cameraChosen: 'first_run_camera_chosen',
  uploadFallbackChosen: 'first_run_upload_fallback_chosen',
  firstScanStarted: 'first_run_first_scan_started',
} as const;

export type FirstRunEvent = (typeof FIRST_RUN_EVENTS)[keyof typeof FIRST_RUN_EVENTS];

export type FirstRunFlag = 'intro' | 'tips' | 'firstScan';

const FLAG_PREFIX = 'plantpal_first_run_';

/** Per-browser first-run flags (localStorage) and funnel-event recording (Vercel Web Analytics `va`). */
@Injectable({ providedIn: 'root' })
export class FirstRunService {
  isSeen(flag: FirstRunFlag): boolean {
    try {
      return localStorage.getItem(FLAG_PREFIX + flag) === '1';
    } catch {
      return true; // storage unavailable (SSR, blocked): never nag
    }
  }

  markSeen(flag: FirstRunFlag): void {
    try {
      localStorage.setItem(FLAG_PREFIX + flag, '1');
    } catch { /* storage unavailable */ }
  }

  track(event: FirstRunEvent): void {
    if (typeof window === 'undefined') return;
    const va = (window as unknown as { va?: (...args: unknown[]) => void }).va;
    va?.('event', { name: event });
  }
}
