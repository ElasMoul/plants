import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent {
  readonly navLinks = [
    { label: 'My Plants', route: '/plants', icon: 'local_florist' },
    { label: 'Identify', route: '/identify', icon: 'search' },
    { label: 'Reminders', route: '/reminders', icon: 'notifications' },
    { label: 'Chat', route: '/chat', icon: 'chat' },
  ];

  constructor(
    public authService: AuthService,
    private router: Router,
  ) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
