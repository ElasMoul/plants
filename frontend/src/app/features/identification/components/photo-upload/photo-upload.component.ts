import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const SpeechRecognitionAPI: any =
  (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

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
  listening = false;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private recognition: any = null;

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
    if (!SpeechRecognitionAPI) return;
    try {
      this.recognition = new SpeechRecognitionAPI();
      this.recognition.continuous = false;
      this.recognition.interimResults = false;
      this.recognition.lang = navigator.language;

      this.recognition.onresult = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
        const transcript: string = event.results[0][0].transcript;
        this.contextText = transcript.substring(0, MAX_CONTEXT_CHARS);
        this.listening = false;
      };

      this.recognition.onerror = (event: any) => { // eslint-disable-line @typescript-eslint/no-explicit-any
        this.listening = false;
        if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
          this.snackBar.open('Microphone unavailable', 'Dismiss', { duration: 4000 });
        }
      };

      this.recognition.onend = () => {
        this.listening = false;
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
