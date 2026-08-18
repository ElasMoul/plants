import { Component, Input } from '@angular/core';
import { SpeechService } from '../../services/speech.service';

@Component({
    selector: 'app-read-aloud-button',
    templateUrl: './read-aloud-button.component.html',
    styleUrls: ['./read-aloud-button.component.scss'],
    standalone: false
})
export class ReadAloudButtonComponent {
  @Input() text = '';
  @Input() ariaLabel = 'Read aloud';

  constructor(readonly speechService: SpeechService) {}

  get isReading(): boolean {
    return this.speechService.isReadingText(this.text);
  }

  toggle(): void {
    if (this.isReading) {
      this.speechService.stop();
    } else {
      this.speechService.speak(this.text);
    }
  }
}
