import { Component, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from './core/services/auth.service';
import { PushNotificationService } from './core/services/push-notification.service';

const NOTIFICATION_PROMPT_KEY = 'plantpal_notifications_prompted';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit {
  readonly navLinks = [
    { label: 'Garden',     route: '/plants',    icon: 'local_florist' },
    { label: 'Identify',   route: '/identify',  icon: 'document_scanner' },
    { label: 'Reminders',  route: '/reminders', icon: 'notifications' },
    { label: 'Chat',       route: '/chat',      icon: 'chat_bubble_outline' },
  ];

  showNotificationBanner = false;

  constructor(
    public authService: AuthService,
    private readonly pushNotificationService: PushNotificationService,
    private readonly snackBar: MatSnackBar,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.showNotificationBanner = this.authService.isLoggedIn() && this.shouldPromptForNotifications();

    // Always land at the top of the new page — Angular's own scroll restoration only resets
    // on forward navigation and restores position on back/forward, which isn't what we want here.
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => window.scrollTo({ top: 0, left: 0 }));
  }

  acceptNotifications(): void {
    this.markPrompted();
    this.showNotificationBanner = false;

    this.pushNotificationService.requestPermission().then(granted => {
      if (!granted) return;
      this.pushNotificationService.subscribeToNotifications().subscribe({
        next: () => {
          this.snackBar.open('Reminders enabled on this device.', undefined, { duration: 3000 });
        },
        error: () => {
          this.snackBar.open('Could not enable notifications.', 'Dismiss', { duration: 4000 });
        },
      });
    });
  }

  dismissNotifications(): void {
    this.markPrompted();
    this.showNotificationBanner = false;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private shouldPromptForNotifications(): boolean {
    if (typeof Notification === 'undefined') return false;
    if (Notification.permission !== 'default') return false;
    return localStorage.getItem(NOTIFICATION_PROMPT_KEY) !== 'true';
  }

  private markPrompted(): void {
    localStorage.setItem(NOTIFICATION_PROMPT_KEY, 'true');
  }
}
