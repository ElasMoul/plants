import { Component, NgZone, OnDestroy } from '@angular/core';

/* eslint-disable @typescript-eslint/no-explicit-any */
const SpeechRecognitionAPI: any =
  (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
/* eslint-enable @typescript-eslint/no-explicit-any */

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

  // ── Mic-only test (getUserMedia + AudioContext, NO recognition) ──────────
  micTesting = false;
  micLevel = 0;

  // ── Recognition test (SpeechRecognition only, NO getUserMedia upfront) ───
  listening = false;
  transcript = '';
  recLevel = 0;   // audio level started from onstart (after recognition owns the mic)

  statusMessage = 'Ready';
  log: LogEntry[] = [];

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private recognition: any = null;
  private micAudioCtx: AudioContext | null = null;
  private micAnalyser: AnalyserNode | null = null;
  private micStream: MediaStream | null = null;
  private micAnimId: number | null = null;
  private micData: Uint8Array | null = null;

  private recAudioCtx: AudioContext | null = null;
  private recAnalyser: AnalyserNode | null = null;
  private recStream: MediaStream | null = null;
  private recAnimId: number | null = null;
  private recData: Uint8Array | null = null;

  private cumulativeFinal = '';

  constructor(private readonly ngZone: NgZone) {}

  ngOnDestroy(): void {
    this.stopMic();
    this.stopRecognition();
  }

  // ── Mic-only test ─────────────────────────────────────────────────────────

  toggleMic(): void {
    if (this.micTesting) {
      this.stopMic();
    } else {
      this.startMic();
    }
  }

  private startMic(): void {
    this.addLog('[MIC] Calling getUserMedia…');
    navigator.mediaDevices.getUserMedia({ audio: true })
      .then(stream => {
        this.micStream = stream;
        this.addLog('[MIC] Stream acquired — mic IS accessible', 'result');
        this.micTesting = true;

        this.micAudioCtx = new AudioContext();
        this.micAnalyser = this.micAudioCtx.createAnalyser();
        this.micAnalyser.fftSize = 512;
        this.micAnalyser.smoothingTimeConstant = 0.4;
        this.micData = new Uint8Array(this.micAnalyser.fftSize);
        this.micAudioCtx.createMediaStreamSource(stream).connect(this.micAnalyser);

        this.ngZone.runOutsideAngular(() => {
          const loop = () => {
            if (!this.micAnalyser || !this.micData) return;
            this.micAnimId = requestAnimationFrame(loop);
            this.micAnalyser.getByteTimeDomainData(this.micData);
            let sq = 0;
            for (let i = 0; i < this.micData.length; i++) {
              const n = (this.micData[i] - 128) / 128;
              sq += n * n;
            }
            const level = Math.min(100, Math.sqrt(sq / this.micData.length) * 500);
            this.ngZone.run(() => { this.micLevel = level; });
          };
          loop();
        });
      })
      .catch((err: DOMException) => {
        this.addLog(`[MIC] getUserMedia FAILED: ${err.name} — ${err.message}`, 'error');
      });
  }

  private stopMic(): void {
    this.micTesting = false;
    this.micLevel = 0;
    if (this.micAnimId !== null) { cancelAnimationFrame(this.micAnimId); this.micAnimId = null; }
    if (this.micAnalyser) { this.micAnalyser.disconnect(); this.micAnalyser = null; }
    if (this.micAudioCtx) { this.micAudioCtx.close().catch(() => { /* AudioContext.close() rejection is harmless */ }); this.micAudioCtx = null; }
    if (this.micStream) { this.micStream.getTracks().forEach(t => t.stop()); this.micStream = null; }
    this.micData = null;
    this.addLog('[MIC] Stopped');
  }

  // ── Recognition test ──────────────────────────────────────────────────────

  toggleRecognition(): void {
    if (this.listening) {
      this.stopRecognition();
    } else {
      this.startRecognition();
    }
  }

  clearAll(): void {
    this.transcript = '';
    this.cumulativeFinal = '';
    this.log = [];
  }

  private startRecognition(): void {
    if (!SpeechRecognitionAPI) {
      this.addLog('[REC] SpeechRecognition not available', 'error');
      return;
    }
    this.cumulativeFinal = '';
    this.addLog('[REC] Calling recognition.start() — no getUserMedia first');
    this.statusMessage = 'Waiting for onstart…';

    this.recognition = new SpeechRecognitionAPI();
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.lang = navigator.language;

    this.recognition.onaudiostart = () => {
      this.ngZone.run(() => this.addLog('[REC] onaudiostart — mic capture open (before cloud connect)'));
    };

    this.recognition.onstart = () => {
      this.ngZone.run(() => {
        this.addLog('[REC] onstart fired — connected to speech service', 'result');
        this.statusMessage = 'Listening…';
        this.listening = true;
        this.startRecLevelMeter();
      });
    };

    this.recognition.onaudioend = () => {
      this.ngZone.run(() => this.addLog('[REC] onaudioend — mic capture closed'));
    };

    this.recognition.onresult = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
      this.ngZone.run(() => {
        let interim = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const text: string = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            this.cumulativeFinal += text + ' ';
            this.addLog(`[REC] FINAL: "${text}"`, 'result');
          } else {
            interim += text;
          }
        }
        if (interim) this.addLog(`[REC] interim: "${interim}"`);
        this.transcript = this.cumulativeFinal + interim;
      });
    };

    this.recognition.onerror = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
      this.ngZone.run(() => {
        const detail = event.message ? ` — ${event.message}` : '';
        this.addLog(`[REC] onerror: ${event.error}${detail}`, 'error');
        this.statusMessage = `Error: ${event.error}`;
      });
    };

    this.recognition.onend = () => {
      this.ngZone.run(() => {
        this.addLog('[REC] onend fired');
        if (this.listening) {
          this.addLog('[REC] Restarting…');
          try { this.recognition.start(); } catch { /* ok */ }
        }
      });
    };

    try {
      this.recognition.start();
      this.addLog('[REC] recognition.start() called — waiting for onstart');
    } catch (e) {
      this.addLog(`[REC] recognition.start() threw: ${String(e)}`, 'error');
    }
  }

  private stopRecognition(): void {
    this.listening = false;
    this.statusMessage = 'Stopped';
    this.recLevel = 0;
    if (this.recognition) { try { this.recognition.stop(); } catch { /* ok */ } this.recognition = null; }
    this.stopRecLevelMeter();
    this.addLog('[REC] Stopped');
  }

  private startRecLevelMeter(): void {
    if (!('mediaDevices' in navigator)) return;
    navigator.mediaDevices.getUserMedia({ audio: true })
      .then(stream => {
        this.recStream = stream;
        this.addLog('[REC-METER] Secondary stream acquired');
        this.recAudioCtx = new AudioContext();
        this.recAnalyser = this.recAudioCtx.createAnalyser();
        this.recAnalyser.fftSize = 512;
        this.recAnalyser.smoothingTimeConstant = 0.4;
        this.recData = new Uint8Array(this.recAnalyser.fftSize);
        this.recAudioCtx.createMediaStreamSource(stream).connect(this.recAnalyser);

        this.ngZone.runOutsideAngular(() => {
          const loop = () => {
            if (!this.recAnalyser || !this.recData) return;
            this.recAnimId = requestAnimationFrame(loop);
            this.recAnalyser.getByteTimeDomainData(this.recData);
            let sq = 0;
            for (let i = 0; i < this.recData.length; i++) {
              const n = (this.recData[i] - 128) / 128;
              sq += n * n;
            }
            const level = Math.min(100, Math.sqrt(sq / this.recData.length) * 500);
            this.ngZone.run(() => { this.recLevel = level; });
          };
          loop();
        });
      })
      .catch((err: DOMException) => {
        this.addLog(`[REC-METER] Secondary stream failed: ${err.name} — recognition still active`, 'error');
      });
  }

  private stopRecLevelMeter(): void {
    if (this.recAnimId !== null) { cancelAnimationFrame(this.recAnimId); this.recAnimId = null; }
    if (this.recAnalyser) { this.recAnalyser.disconnect(); this.recAnalyser = null; }
    if (this.recAudioCtx) { this.recAudioCtx.close().catch(() => { /* AudioContext.close() rejection is harmless */ }); this.recAudioCtx = null; }
    if (this.recStream) { this.recStream.getTracks().forEach(t => t.stop()); this.recStream = null; }
    this.recData = null;
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
}
