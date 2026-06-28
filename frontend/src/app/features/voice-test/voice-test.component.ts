import { Component, NgZone, OnDestroy } from '@angular/core';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const SpeechRecognitionAPI: any =
  (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

interface LogEntry {
  time: string;
  msg: string;
  type: 'info' | 'error' | 'result';
}

@Component({
  selector: 'app-voice-test',
  templateUrl: './voice-test.component.html',
  styleUrls: ['./voice-test.component.scss'],
})
export class VoiceTestComponent implements OnDestroy {
  readonly hasSpeechAPI = !!SpeechRecognitionAPI;
  readonly isSecureCtx = window.isSecureContext;
  readonly hostname = location.hostname;
  readonly lang = navigator.language;
  readonly userAgent = navigator.userAgent;

  listening = false;
  statusMessage = 'Ready';
  transcript = '';
  audioLevel = 0;
  log: LogEntry[] = [];

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private recognition: any = null;
  private audioContext: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private mediaStream: MediaStream | null = null;
  private animFrameId: number | null = null;
  private dataArray: Uint8Array | null = null;
  private cumulativeFinal = '';

  constructor(private readonly ngZone: NgZone) {}

  ngOnDestroy(): void {
    this.stopAll();
  }

  toggle(): void {
    if (this.listening) {
      this.stopAll();
    } else {
      this.startAll();
    }
  }

  clearAll(): void {
    this.transcript = '';
    this.cumulativeFinal = '';
    this.log = [];
  }

  private addLog(msg: string, type: LogEntry['type'] = 'info'): void {
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    const m = String(now.getMinutes()).padStart(2, '0');
    const s = String(now.getSeconds()).padStart(2, '0');
    const ms = String(now.getMilliseconds()).padStart(3, '0');
    this.log.unshift({ time: `${h}:${m}:${s}.${ms}`, msg, type });
    if (this.log.length > 100) this.log.pop();
  }

  private startAll(): void {
    if (!SpeechRecognitionAPI) {
      this.statusMessage = 'SpeechRecognition not available in this browser.';
      return;
    }
    this.addLog('Requesting microphone access…');
    this.statusMessage = 'Requesting mic access…';

    navigator.mediaDevices.getUserMedia({ audio: true })
      .then(stream => {
        this.mediaStream = stream;
        this.addLog('Mic granted — starting audio meter and recognition');
        this.startAudioMeter(stream);
        this.startRecognition();
        this.listening = true;
        this.statusMessage = 'Listening…';
      })
      .catch((err: DOMException) => {
        this.addLog(`getUserMedia failed: ${err.name} — ${err.message}`, 'error');
        this.statusMessage = `Mic error: ${err.name}`;
      });
  }

  private startAudioMeter(stream: MediaStream): void {
    this.audioContext = new AudioContext();
    this.analyser = this.audioContext.createAnalyser();
    this.analyser.fftSize = 512;
    this.analyser.smoothingTimeConstant = 0.4;
    this.dataArray = new Uint8Array(this.analyser.fftSize);

    const source = this.audioContext.createMediaStreamSource(stream);
    source.connect(this.analyser);

    this.ngZone.runOutsideAngular(() => {
      const loop = () => {
        if (!this.analyser || !this.dataArray) return;
        this.animFrameId = requestAnimationFrame(loop);
        this.analyser.getByteTimeDomainData(this.dataArray);
        let sumSquares = 0;
        for (let i = 0; i < this.dataArray.length; i++) {
          const norm = (this.dataArray[i] - 128) / 128;
          sumSquares += norm * norm;
        }
        const rms = Math.sqrt(sumSquares / this.dataArray.length);
        const level = Math.min(100, rms * 500);
        this.ngZone.run(() => { this.audioLevel = level; });
      };
      loop();
    });
  }

  private startRecognition(): void {
    this.recognition = new SpeechRecognitionAPI();
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.lang = navigator.language;

    this.recognition.onstart = () => {
      this.ngZone.run(() => this.addLog('onstart — recognition is active'));
    };

    this.recognition.onresult = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
      this.ngZone.run(() => {
        let currentInterim = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const text: string = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            this.cumulativeFinal += text + ' ';
            this.addLog(`FINAL: "${text}"`, 'result');
          } else {
            currentInterim += text;
          }
        }
        if (currentInterim) {
          this.addLog(`interim: "${currentInterim}"`);
        }
        this.transcript = this.cumulativeFinal + currentInterim;
      });
    };

    this.recognition.onerror = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
      this.ngZone.run(() => {
        const detail = event.message ? ` — ${event.message}` : '';
        this.addLog(`onerror: ${event.error}${detail}`, 'error');
        if (event.error !== 'no-speech') {
          this.statusMessage = `Recognition error: ${event.error}`;
        }
      });
    };

    this.recognition.onend = () => {
      this.ngZone.run(() => {
        this.addLog('onend fired');
        if (this.listening) {
          this.addLog('Restarting recognition (continuous mode)…');
          try { this.recognition.start(); } catch { /* already starting */ }
        }
      });
    };

    try {
      this.recognition.start();
      this.addLog('recognition.start() called');
    } catch (e) {
      this.addLog(`recognition.start() threw: ${String(e)}`, 'error');
    }
  }

  private stopAll(): void {
    this.listening = false;
    this.audioLevel = 0;
    this.statusMessage = 'Stopped';

    if (this.animFrameId !== null) {
      cancelAnimationFrame(this.animFrameId);
      this.animFrameId = null;
    }

    if (this.recognition) {
      try { this.recognition.stop(); } catch { /* ok */ }
      this.recognition = null;
    }

    if (this.analyser) {
      this.analyser.disconnect();
      this.analyser = null;
    }

    if (this.audioContext) {
      this.audioContext.close().catch(() => {});
      this.audioContext = null;
    }

    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(t => t.stop());
      this.mediaStream = null;
    }

    this.dataArray = null;
    this.addLog('Stopped — resources released');
  }
}
