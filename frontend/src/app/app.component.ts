import { translate } from './shared/i18n/language.service';
import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { EMPTY } from 'rxjs';
import { catchError, filter } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '@plantpal/shared-core';
import { PushNotificationService } from './core/services/push-notification.service';
import { AdminService } from './features/admin/admin.service';
import { SessionMonitorService } from './core/services/session-monitor.service';

const NOTIFICATION_PROMPT_KEY = 'plantpal_notifications_prompted';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    standalone: false
})
export class AppComponent implements OnInit {
  readonly navLinks = [
    { label: translate('Home'),       route: '/home',      icon: 'home' },
    { label: translate('Garden'),     route: '/garden',    icon: 'local_florist' },
    { label: translate('Identify'),   route: '/identify',  icon: 'document_scanner' },
    { label: translate('Reminders'),  route: '/reminders', icon: 'notifications' },
    { label: translate('Chat'),       route: '/chat',      icon: 'chat_bubble_outline' },
  ];

  showNotificationBanner = false;
  isAdministrator = false;
  get isAdminPage(): boolean { return this.router.url.startsWith('/admin'); }

  constructor(
    public authService: AuthService,
    private readonly pushNotificationService: PushNotificationService,
    private readonly sessionMonitorService: SessionMonitorService,
    private readonly snackBar: MatSnackBar,
    private readonly http: HttpClient,
    private router: Router,
    private readonly adminService: AdminService,
  ) {}

  ngOnInit(): void {
    this.showNotificationBanner = this.authService.isLoggedIn() && this.shouldPromptForNotifications();
    this.sessionMonitorService.start();
    this.refreshAccess();

    // Always land at the top of the new page — Angular's own scroll restoration only resets
    // on forward navigation and restores position on back/forward, which isn't what we want here.
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => { window.scrollTo({ top: 0, left: 0 }); this.refreshAccess(); });
  }

  private refreshAccess(): void {
    if (!this.authService.isLoggedIn()) { this.isAdministrator = false; return; }
    this.adminService.access().subscribe({
      next: access => this.isAdministrator = access.administrator,
      error: () => this.isAdministrator = false,
    });
  }

  acceptNotifications(): void {
    this.markPrompted();
    this.showNotificationBanner = false;

    this.pushNotificationService.requestPermission().then(granted => {
      if (!granted) return;
      this.pushNotificationService.subscribeToNotifications().subscribe({
        next: () => {
          this.snackBar.open(translate('Reminders enabled on this device.'), undefined, { duration: 3000 });
        },
        error: () => {
          this.snackBar.open(translate('Could not enable notifications.'), translate('Dismiss'), { duration: 4000 });
        },
      });
    });
  }

  dismissNotifications(): void {
    this.markPrompted();
    this.showNotificationBanner = false;
  }

  logout(): void {
    this.sessionMonitorService.stop();
    // Local sign-out must never wait for the network, but tell the inert registry about an
    // explicit user decision so its future enforcement has a real revocation record.
    this.http.post<void>('/api/v1/auth/logout', {}).pipe(catchError(() => EMPTY)).subscribe();
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
