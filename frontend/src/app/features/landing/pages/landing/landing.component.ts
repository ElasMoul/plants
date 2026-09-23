import { translate } from '../../../../shared/i18n/language.service';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@plantpal/shared-core';

interface HowItWorksStep {
  icon: string;
  title: string;
  description: string;
}

interface FeatureCard {
  icon: string;
  title: string;
  description: string;
}

const HOW_IT_WORKS: HowItWorksStep[] = [
  {
    icon: 'add_a_photo',
    title: translate('Snap a photo'),
    description: translate('Take a picture of any plant, leaf, or flower — no special equipment needed.'),
  },
  {
    icon: 'document_scanner',
    title: translate('Get an instant diagnosis'),
    description:
      translate('AI identifies the species and checks for signs of disease, pests, or stress in seconds.'),
  },
  {
    icon: 'checklist',
    title: translate('Follow a care plan'),
    description: translate('Receive a personalised watering, feeding, and treatment schedule — with reminders.'),
  },
];

const FEATURES: FeatureCard[] = [
  {
    icon: 'yard',
    title: translate('AI species identification'),
    description:
      translate('Powered by multiple AI models — including Claude, GPT-4o, and PlantNet — for fast, accurate identification of thousands of species.'),
  },
  {
    icon: 'notifications_active',
    title: translate('Personalised care reminders'),
    description:
      translate('Never miss a watering, fertilizing, or repotting day again. Reminders arrive as push notifications, tailored to each plant.'),
  },
  {
    icon: 'healing',
    title: translate('Disease detection & treatment'),
    description:
      translate('Spot issues early. Every diagnosis comes with a step-by-step treatment plan you can track through to completion.'),
  },
  {
    icon: 'forum',
    title: translate('AI plant-care chat'),
    description:
      translate('Ask anything, anytime. Your assistant knows your garden and gives advice specific to the plants you actually own.'),
  },
  {
    icon: 'menu_book',
    title: translate('Shared species knowledge'),
    description:
      translate('Every identification enriches a community botanical knowledge base — so care advice keeps getting better for everyone.'),
  },
];

@Component({
    selector: 'app-landing',
    templateUrl: './landing.component.html',
    styleUrls: ['./landing.component.scss'],
    standalone: false
})
export class LandingComponent implements OnInit {
  readonly howItWorks = HOW_IT_WORKS;
  readonly features = FEATURES;
  readonly currentYear = new Date().getFullYear();

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/home']);
    }
  }
}
