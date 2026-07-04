import { Component, EventEmitter, Input, isDevMode, NgZone, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSelectChange } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PlantService } from '../../../plant/services/plant.service';
import { PlantResponse } from '../../../plant/models/plant.model';
import { AnalyzeEmitPayload } from '../../models/identification.model';

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
const MAX_IMAGES = 5;
const MAX_CONTEXT_CHARS = 500;

/* eslint-disable @typescript-eslint/no-explicit-any */
const SpeechRecognitionAPI: any =
  (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
/* eslint-enable @typescript-eslint/no-explicit-any */

if (isDevMode() && SpeechRecognitionAPI && !window.isSecureContext && location.hostname !== 'localhost') {
  console.warn('[PhotoUpload] SpeechRecognition detected but isSecureContext=false — mic button disabled. Access the app over HTTPS or localhost to enable voice input.');
}

interface ImageEntry {
  file: File;
  preview: string;
  organ: string;
}

@Component({
  selector: 'app-photo-upload',
  templateUrl: './photo-upload.component.html',
  styleUrls: ['./photo-upload.component.scss'],
})
export class PhotoUploadComponent implements OnInit, OnDestroy {
  @Input() lockedPlantId?: number;
  @Input() lockedSpeciesId?: number;
  @Output() readonly analyze = new EventEmitter<AnalyzeEmitPayload>();

  entries: ImageEntry[] = [];
  plants: PlantResponse[] = [];
  selectedPlantId: number | undefined;
  isDragOver = false;
  validationErrors: string[] = [];
  batchMode = false;

  contextExpanded = false;
  contextText = '';
  readonly maxContextChars = MAX_CONTEXT_CHARS;
  readonly speechSupported = !!SpeechRecognitionAPI;
  readonly speechSecure = window.isSecureContext || location.hostname === 'localhost';
  listening = false;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private recognition: any = null;
  private recognitionBaseText = '';

  get batchModeAvailable(): boolean {
    return this.lockedPlantId == null && this.lockedSpeciesId == null;
  }

  get contextCharsLeft(): number {
    return MAX_CONTEXT_CHARS - this.contextText.length;
  }

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly plantService: PlantService,
    private readonly snackBar: MatSnackBar,
    private readonly ngZone: NgZone,
  ) {}

  ngOnInit(): void {
    if (this.lockedPlantId != null) {
      this.selectedPlantId = this.lockedPlantId;
      return;
    }

    this.plantService
      .getPlants(0, 100)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.plants = res.data.content;
        },
        error: () => {
          this.plants = [];
        },
      });
  }

  ngOnDestroy(): void {
    this.stopListening();
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleContext(): void {
    this.contextExpanded = !this.contextExpanded;
    if (!this.contextExpanded) {
      this.stopListening();
    }
  }

  toggleListening(): void {
    if (this.listening) {
      this.stopListening();
      return;
    }
    this.startListening();
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    this.processFiles(event.dataTransfer?.files ?? null);
  }

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.processFiles(input.files);
    input.value = ''; // allow re-selecting same file
  }

  onOrganChange(index: number, event: MatSelectChange): void {
    this.entries[index] = { ...this.entries[index], organ: event.value as string };
  }

  onPlantChange(event: MatSelectChange): void {
    this.selectedPlantId = event.value as number | undefined;
  }

  onBatchModeChange(checked: boolean): void {
    this.batchMode = checked;
    if (checked) {
      this.selectedPlantId = undefined;
      this.entries = this.entries.map(e => ({ ...e, organ: 'auto' }));
    }
  }

  removeImage(index: number): void {
    this.entries = this.entries.filter((_, i) => i !== index);
    this.validationErrors = [];
  }

  onAnalyze(): void {
    const trimmed = this.contextText.trim();
    this.analyze.emit({
      images: this.entries.map(e => e.file),
      organs: this.entries.map(e => e.organ),
      plantId: this.selectedPlantId,
      userContext: trimmed || undefined,
    });
  }

  private startListening(): void {
    if (!SpeechRecognitionAPI || !this.speechSecure) return;
    // Call recognition.start() directly — no getUserMedia pre-check.
    // getUserMedia before recognition.start() causes mic contention on Chromium:
    // the speech service cannot acquire the device a second time, onstart never
    // fires, onend arrives silently ~4s later. SpeechRecognition handles its own
    // mic capture and permission dialog; onerror covers 'not-allowed' below.
    this.doStartRecognition();
  }

  private doStartRecognition(): void {
    try {
      this.recognition = new SpeechRecognitionAPI();
      this.recognition.continuous = false;
      this.recognition.interimResults = true;
      this.recognition.lang = navigator.language;

      this.recognitionBaseText = this.contextText.trim();

      this.recognition.onresult = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
        this.ngZone.run(() => {
          let finalTranscript = '';
          let interimTranscript = '';
          for (let i = 0; i < event.results.length; i++) {
            const t: string = event.results[i][0].transcript;
            if (event.results[i].isFinal) {
              finalTranscript += t;
            } else {
              interimTranscript += t;
            }
          }
          const spoken = finalTranscript || interimTranscript;
          const combined = this.recognitionBaseText ? `${this.recognitionBaseText} ${spoken}` : spoken;
          this.contextText = combined.substring(0, MAX_CONTEXT_CHARS);
        });
      };

      this.recognition.onerror = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
        this.ngZone.run(() => {
          this.listening = false;
          if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
            this.snackBar.open('Microphone unavailable', 'Dismiss', { duration: 4000 });
          }
        });
      };

      this.recognition.onend = () => {
        this.ngZone.run(() => { this.listening = false; });
      };

      this.recognition.start();
      this.listening = true;
    } catch {
      this.snackBar.open('Microphone unavailable', 'Dismiss', { duration: 4000 });
    }
  }

  private stopListening(): void {
    if (this.recognition) {
      try { this.recognition.stop(); } catch { /* already stopped */ }
      this.recognition = null;
    }
    this.listening = false;
  }

  private processFiles(fileList: FileList | null): void {
    if (!fileList) return;

    const errors: string[] = [];
    const toAdd: File[] = [];

    Array.from(fileList).forEach(file => {
      if (this.entries.length + toAdd.length >= MAX_IMAGES) {
        errors.push(`"${file.name}" skipped — maximum ${MAX_IMAGES} images allowed`);
        return;
      }
      const err = this.validateFile(file);
      if (err) {
        errors.push(err);
      } else {
        toAdd.push(file);
      }
    });

    this.validationErrors = errors;

    toAdd.forEach(file => {
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        const result = e.target?.result;
        if (typeof result === 'string') {
          this.entries = [...this.entries, { file, preview: result, organ: 'auto' }];
        }
      };
      reader.readAsDataURL(file);
    });
  }

  private validateFile(file: File): string | null {
    if (!ALLOWED_TYPES.includes(file.type)) {
      return `"${file.name}": unsupported format — JPEG, PNG, or WebP only`;
    }
    if (file.size > MAX_SIZE_BYTES) {
      return `"${file.name}": exceeds 10 MB limit`;
    }
    return null;
  }
}
