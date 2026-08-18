import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';

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
    title: 'Snap a photo',
    description: 'Take a picture of any plant, leaf, or flower — no special equipment needed.',
  },
  {
    icon: 'document_scanner',
    title: 'Get an instant diagnosis',
    description:
      'AI identifies the species and checks for signs of disease, pests, or stress in seconds.',
  },
  {
    icon: 'checklist',
    title: 'Follow a care plan',
    description: 'Receive a personalised watering, feeding, and treatment schedule — with reminders.',
  },
];

const FEATURES: FeatureCard[] = [
  {
    icon: 'yard',
    title: 'AI species identification',
    description:
      'Powered by multiple AI models — including Claude, GPT-4o, and PlantNet — for fast, accurate identification of thousands of species.',
  },
  {
    icon: 'notifications_active',
    title: 'Personalised care reminders',
    description:
      'Never miss a watering, fertilizing, or repotting day again. Reminders arrive as push notifications, tailored to each plant.',
  },
  {
    icon: 'healing',
    title: 'Disease detection & treatment',
    description:
      'Spot issues early. Every diagnosis comes with a step-by-step treatment plan you can track through to completion.',
  },
  {
    icon: 'forum',
    title: 'AI plant-care chat',
    description:
      'Ask anything, anytime. Your assistant knows your garden and gives advice specific to the plants you actually own.',
  },
  {
    icon: 'menu_book',
    title: 'Shared species knowledge',
    description:
      'Every identification enriches a community botanical knowledge base — so care advice keeps getting better for everyone.',
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
