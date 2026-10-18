import { Component, OnInit } from '@angular/core';
import { FIRST_RUN_EVENTS, FirstRunService } from '../../../core/services/first-run.service';

@Component({
  selector: 'app-first-run-intro',
  templateUrl: './first-run-intro.component.html',
  styleUrls: ['./first-run-intro.component.scss'],
  standalone: false,
})
export class FirstRunIntroComponent implements OnInit {
  visible = false;
  step = 0;
  readonly steps = [
    { icon: 'add_a_photo', title: 'Photograph your plant', text: 'Take or upload a clear photo of the plant.' },
    { icon: 'biotech', title: 'Get it identified', text: 'We identify the species and check its health.' },
    { icon: 'notifications', title: 'Follow its care schedule', text: 'Get a care plan with reminders for watering and more.' },
  ];

  constructor(private readonly firstRun: FirstRunService) {}

  ngOnInit(): void {
    this.visible = !this.firstRun.isSeen('intro');
    if (this.visible) this.firstRun.track(FIRST_RUN_EVENTS.introShown);
  }

  get isLast(): boolean {
    return this.step === this.steps.length - 1;
  }

  next(): void {
    if (this.isLast) {
      this.close(FIRST_RUN_EVENTS.introFinished);
    } else {
      this.step++;
    }
  }

  skip(): void {
    this.close(FIRST_RUN_EVENTS.introSkipped);
  }

  private close(event: typeof FIRST_RUN_EVENTS.introSkipped | typeof FIRST_RUN_EVENTS.introFinished): void {
    this.firstRun.markSeen('intro');
    this.firstRun.track(event);
    this.visible = false;
  }
}
