import { Component } from '@angular/core';
import { translate } from '../../i18n/language.service';

@Component({
  selector: 'app-beta-notice',
  template: '<p class="beta-notice" data-testid="beta-notice">{{ text }}</p>',
  styles: [`
    .beta-notice {
      margin-block: 24px 8px;
      padding-inline: 16px;
      text-align: center;
      font-size: 0.75rem;
      color: var(--color-text-secondary, #6b7280);
    }
  `],
  standalone: false,
})
export class BetaNoticeComponent {
  readonly text = translate('This app is still in beta');
}
