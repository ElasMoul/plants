import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { LANGUAGES, readLanguage, translate } from '../i18n/language.service';
import { aiText } from '../i18n/ai-text.pipe';

@Injectable({ providedIn: 'root' })
export class SpeechService implements OnDestroy {
  readonly isSupported = 'speechSynthesis' in window;
  speaking = false;
  currentText = '';
  error = '';
  failedText = '';
  private request = 0;

  constructor(private readonly ngZone: NgZone) {}

  async speak(text: string): Promise<void> {
    this.stop();
    if (!text || !this.isSupported) return;
    const request = this.request;
    const language = readLanguage();
    const spoken = aiText(text);
    if (language === 'ar' && !/[\u0600-\u06ff]/.test(spoken)) {
      this.fail(text, 'Arabic text is not ready to read aloud yet.');
      return;
    }
    const voices = await this.loadVoices();
    if (request !== this.request) return;
    const locale = LANGUAGES.find(item => item.code === language)!.locale;
    const voice = voices.find(item => item.lang.toLowerCase() === locale.toLowerCase())
      ?? voices.find(item => item.lang.toLowerCase().replace('_', '-').split('-')[0] === language);
    if (!voice) {
      this.fail(text, 'No voice is available for this language. Install a matching voice in your device speech settings.');
      return;
    }
    const utterance = new SpeechSynthesisUtterance(spoken);
    utterance.rate = 0.95;
    utterance.voice = voice;
    utterance.lang = voice.lang;
    utterance.onstart = () => this.ngZone.run(() => {
      if (request !== this.request) return;
      this.speaking = true;
      this.currentText = text;
    });
    utterance.onend = () => this.ngZone.run(() => {
      if (request !== this.request) return;
      this.speaking = false;
      this.currentText = '';
    });
    utterance.onerror = () => this.ngZone.run(() => {
      if (request !== this.request) return;
      this.speaking = false;
      this.currentText = '';
      this.fail(text, 'Could not read this text aloud. Please try again.');
    });
    window.speechSynthesis.speak(utterance);
  }

  stop(): void {
    this.request++;
    if (this.isSupported) window.speechSynthesis.cancel();
    this.speaking = false;
    this.currentText = '';
    this.error = '';
    this.failedText = '';
  }

  isReadingText(text: string): boolean { return this.speaking && this.currentText === text; }
  ngOnDestroy(): void { this.stop(); }

  private fail(text: string, message: string): void {
    this.ngZone.run(() => { this.failedText = text; this.error = translate(message); });
  }

  private loadVoices(): Promise<SpeechSynthesisVoice[]> {
    const synth = window.speechSynthesis;
    const current = synth.getVoices();
    if (current.length) return Promise.resolve(current);
    return new Promise(resolve => {
      const finish = () => {
        clearTimeout(timer);
        synth.removeEventListener('voiceschanged', finish);
        resolve(synth.getVoices());
      };
      const timer = setTimeout(finish, 2000);
      synth.addEventListener('voiceschanged', finish);
    });
  }
}
