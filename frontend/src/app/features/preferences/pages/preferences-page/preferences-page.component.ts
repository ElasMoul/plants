import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-preferences-page',
  templateUrl: './preferences-page.component.html',
  styleUrls: ['./preferences-page.component.scss'],
})
export class PreferencesPageComponent {
  constructor(
    private readonly location: Location,
    private readonly router: Router,
  ) {}

  close(): void {
    // history.state.navigationId > 1 means we actually navigated here (not a fresh tab/refresh
    // landing directly on /preferences) — only then is location.back() safe to use.
    if (history.state?.navigationId > 1) {
      this.location.back();
    } else {
      this.router.navigate(['/home']);
    }
  }
}
