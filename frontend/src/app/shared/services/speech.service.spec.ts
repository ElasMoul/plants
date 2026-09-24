import { NgZone } from '@angular/core';
import { SpeechService } from './speech.service';
import { rememberAiTexts } from '../i18n/ai-text.pipe';

describe('Language-matched speech', () => {
  let service: SpeechService;
  let voices: SpeechSynthesisVoice[];
  let events: EventTarget;
  let speak: jest.Mock;
  const voice = (lang: string) => ({ lang, name: lang }) as SpeechSynthesisVoice;
  beforeEach(() => {
    voices = [voice('en-US'), voice('ar-SA')]; events = new EventTarget(); speak = jest.fn();
    Object.defineProperty(window, 'speechSynthesis', { configurable: true, value: {
      getVoices: () => voices, speak, cancel: jest.fn(),
      addEventListener: events.addEventListener.bind(events),
      removeEventListener: events.removeEventListener.bind(events),
    } });
    Object.defineProperty(window, 'SpeechSynthesisUtterance', { configurable: true,
      value: class { constructor(public text: string) {} } });
    service = new SpeechService({ run: (fn: () => void) => fn() } as NgZone);
    localStorage.setItem('plantpal.language', 'ar');
  });
  afterEach(() => { service.stop(); localStorage.clear(); jest.useRealTimers(); });
  it('uses saved Arabic text and explicitly selects an Arabic voice, never the default English one', async () => {
    rememberAiTexts({ 'Water the roots': 'اسقِ الجذور' });
    await service.speak('Water the roots');
    expect(speak).toHaveBeenCalledWith(expect.objectContaining({ text: 'اسقِ الجذور', lang: 'ar-SA', voice: voices[1] }));
  });
  it('shows a clear error without speaking English when no Arabic voice exists', async () => {
    voices = [voice('en-US')];
    await service.speak('اسقِ الجذور');
    expect(speak).not.toHaveBeenCalled();
    expect(service.error).toContain('لا يتوفر صوت');
  });
  it('waits for asynchronously loaded voices', async () => {
    voices = [];
    const result = service.speak('اسقِ الجذور');
    voices = [voice('ar-MA')];
    events.dispatchEvent(new Event('voiceschanged'));
    await result;
    expect(speak).toHaveBeenCalledWith(expect.objectContaining({ lang: 'ar-MA' }));
  });
  it('does not speak after the user cancels while voices are loading', async () => {
    voices = [];
    const result = service.speak('اسقِ الجذور');
    service.stop(); voices = [voice('ar-MA')];
    events.dispatchEvent(new Event('voiceschanged')); await result;
    expect(speak).not.toHaveBeenCalled();
  });
});
