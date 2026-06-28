import { Injectable, NgZone, OnDestroy } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SpeechService implements OnDestroy {
  readonly isSupported = 'speechSynthesis' in window;
  speaking = false;
  currentText = '';

  constructor(private readonly ngZone: NgZone) {}

  speak(text: string): void {
    window.speechSynthesis.cancel();
    if (!text || !this.isSupported) return;
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 0.95;
    utterance.lang = document.documentElement.lang || navigator.language;
    utterance.onstart = () => this.ngZone.run(() => {
      this.speaking = true;
      this.currentText = text;
    });
    utterance.onend = () => this.ngZone.run(() => {
      this.speaking = false;
      this.currentText = '';
    });
    utterance.onerror = () => this.ngZone.run(() => {
      this.speaking = false;
      this.currentText = '';
    });
    window.speechSynthesis.speak(utterance);
  }

  stop(): void {
    window.speechSynthesis.cancel();
    this.speaking = false;
    this.currentText = '';
  }

  isReadingText(text: string): boolean {
    return this.speaking && this.currentText === text;
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
