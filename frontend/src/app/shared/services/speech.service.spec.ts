import { NgZone } from '@angular/core';
import { SpeechService } from './speech.service';

describe('Language-matched speech', () => {
  let service: SpeechService;
  let voices: SpeechSynthesisVoice[];
  let events: EventTarget;
  let speak: jest.Mock;
  const voice = (lang: string) => ({ lang, name: lang }) as SpeechSynthesisVoice;
  beforeEach(() => {
    voices = [voice('en-US'), voice('fr-FR')]; events = new EventTarget(); speak = jest.fn();
    Object.defineProperty(window, 'speechSynthesis', { configurable: true, value: {
      getVoices: () => voices, speak, cancel: jest.fn(),
      addEventListener: events.addEventListener.bind(events),
      removeEventListener: events.removeEventListener.bind(events),
    } });
    Object.defineProperty(window, 'SpeechSynthesisUtterance', { configurable: true,
      value: class { constructor(public text: string) {} } });
    service = new SpeechService({ run: (fn: () => void) => fn() } as NgZone);
    localStorage.setItem('plantpal.language', 'fr');
  });
  afterEach(() => { service.stop(); localStorage.clear(); jest.useRealTimers(); });
  it('uses saved French text and explicitly selects a French voice, never the default English one', async () => {
    await service.speak('Arrosez les racines', 'fr');
    expect(speak).toHaveBeenCalledWith(expect.objectContaining({ text: 'Arrosez les racines', lang: 'fr-FR', voice: voices[1] }));
  });
  it('uses the displayed section language when the interface is French', async () => {
    await service.speak('Water the roots', 'en');
    expect(speak).toHaveBeenCalledWith(expect.objectContaining({ text: 'Water the roots', voice: voices[0], lang: 'en-US' }));
  });
  it('shows a clear error without speaking English when no matching voice exists', async () => {
    voices = [voice('en-US')];
    await service.speak('Arrosez les racines');
    expect(speak).not.toHaveBeenCalled();
    expect(service.error).toContain('Aucune voix disponible');
  });
  it('waits for asynchronously loaded voices', async () => {
    voices = [];
    const result = service.speak('Arrosez les racines');
    voices = [voice('fr-CA')];
    events.dispatchEvent(new Event('voiceschanged'));
    await result;
    expect(speak).toHaveBeenCalledWith(expect.objectContaining({ lang: 'fr-CA' }));
  });
  it('does not speak after the user cancels while voices are loading', async () => {
    voices = [];
    const result = service.speak('Arrosez les racines');
    service.stop(); voices = [voice('fr-CA')];
    events.dispatchEvent(new Event('voiceschanged')); await result;
    expect(speak).not.toHaveBeenCalled();
  });
});
