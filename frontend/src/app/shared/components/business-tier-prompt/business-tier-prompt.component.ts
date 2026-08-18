import { Component, Input, OnInit } from '@angular/core';
import { Router } from '@angular/router';

const DISMISS_KEY = 'plantpal_business_tier_prompt_dismissed';
const PLANT_THRESHOLD = 10;

// Friendly, self-declared business/professional-tier upsell (D027, PP-086). Purely
// presentational — no client-side enforcement, no gating of features. Shown on the Home
// dashboard once a garden reaches PLANT_THRESHOLD plants and the user hasn't already
// self-declared businessTier or dismissed the prompt. Dismissal persists in localStorage (not
// just this session) so it doesn't nag on every visit — re-showing every session was considered
// and rejected as too naggy for a purely optional, no-enforcement prompt.
@Component({
    selector: 'app-business-tier-prompt',
    templateUrl: './business-tier-prompt.component.html',
    styleUrls: ['./business-tier-prompt.component.scss'],
    standalone: false
})
export class BusinessTierPromptComponent implements OnInit {
  @Input() totalPlants = 0;
  @Input() businessTier = false;

  dismissed = false;

  constructor(private readonly router: Router) {}

  ngOnInit(): void {
    this.dismissed = localStorage.getItem(DISMISS_KEY) === 'true';
  }

  get visible(): boolean {
    return !this.businessTier && this.totalPlants >= PLANT_THRESHOLD && !this.dismissed;
  }

  dismiss(): void {
    this.dismissed = true;
    localStorage.setItem(DISMISS_KEY, 'true');
  }

  goToPreferences(): void {
    this.router.navigate(['/preferences']);
  }
}
