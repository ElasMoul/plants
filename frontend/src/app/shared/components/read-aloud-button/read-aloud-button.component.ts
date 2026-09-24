import { translate } from '../../i18n/language.service';
import { SectionLanguageState } from '../../i18n/section-language.state';
import { Component, Input, Optional } from '@angular/core';
import { SpeechService } from '../../services/speech.service';

@Component({
    selector: 'app-read-aloud-button',
    templateUrl: './read-aloud-button.component.html',
    styleUrls: ['./read-aloud-button.component.scss'],
    standalone: false
})
export class ReadAloudButtonComponent {
  @Input() text = '';
  @Input() ariaLabel = translate('Read aloud');

  constructor(readonly speechService: SpeechService, @Optional() private readonly section: SectionLanguageState | null = null) {}

  get isReading(): boolean {
    return this.speechService.isReadingText(this.text);
  }

  toggle(): void {
    if (this.isReading) {
      this.speechService.stop();
    } else {
      void this.speechService.speak(this.text, this.section?.language);
    }
  }
}
