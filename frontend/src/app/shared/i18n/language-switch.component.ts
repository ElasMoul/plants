import { Component } from '@angular/core';
import { LanguageService } from './language.service';

@Component({
  selector: 'app-language-switch',
  standalone: true,
  template: `<label class="language-switch">
    <select aria-label="Language / Langue / اللغة" [value]="language.language" (change)="language.select($any($event.target).value)">
      <option value="en" lang="en" title="English">EN</option>
      <option value="fr" lang="fr" title="Français">FR</option>
      <option value="ar" lang="ar" title="العربية">AR</option>
    </select></label>`,
  styles: [`.language-switch { display:flex; align-items:center; gap:4px; margin-inline:8px; }
    select { font:inherit; font-size:14px; color:inherit; background:transparent; border:1px solid #7a967f;
      border-radius:18px; padding:6px 8px; max-width:112px; cursor:pointer; }
    select:focus-visible { outline:2px solid #23713b; outline-offset:3px; }`],
})
export class LanguageSwitchComponent {
  constructor(public readonly language: LanguageService) {}
}
