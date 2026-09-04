import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SettingsStore } from '../settings/settings.store';
import { type ActiveForm, WorldActionsService } from '../world/world-actions.service';
import { CARE_TYPES, CareType } from '../world/world.dto';
import { WorldStore } from '../world/world.store';

/** "WATERING" -> "Watering", "BEGINNER_TIP" -> "Beginner tip". */
function careLabel(t: string): string {
  const words = t.toLowerCase().split('_').join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * The in-world mutation form — a sheet in the design system's own material
 * (sheet fill, membrane hairlines, sec heading, rows of labelled fields,
 * terracotta stake to commit, quiet stake to cancel). Composition per
 * "extending this system": chrome register rules, no tick, one key rule at the
 * attached edge. It floats over the board and never moves the camera.
 */
@Component({
  selector: 'rz-stake-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    @if (actions.activeForm(); as form) {
      <div class="rz-form-scrim" (click)="actions.activeForm.set(null)"></div>
      <section class="rz-form" role="dialog" aria-modal="true" [attr.aria-label]="title()" (click)="$event.stopPropagation()">
        <h3 class="sec">{{ title() }}</h3>
        @if (form.kind === 'identify') {
          <label class="rz-field"><span>Photo of the plant</span>
            <input type="file" accept="image/*" (change)="onFiles($event)" /></label>
          @if (files().length) {
            <p class="rz-form__note">{{ files()[0].name }} · {{ (files()[0].size / 1024).toFixed(0) }} KB</p>
          }
          <label class="rz-field"><span>What is in the photo</span>
            <select [(ngModel)]="organ">
              <option value="leaf">Leaf</option>
              <option value="flower">Flower</option>
              <option value="fruit">Fruit</option>
              <option value="bark">Bark or stem</option>
              <option value="habit">The whole plant</option>
            </select></label>
          <label class="rz-field"><span>Anything you noticed (optional)</span>
            <textarea rows="2" [(ngModel)]="context" placeholder="Brown spots on the lower leaves…"></textarea></label>
          <p class="rz-form__note">The scan runs asynchronously — the answer arrives into the Identification node, and a matching species takes a free cell.</p>
          <div class="btn-row">
            <button class="stake" type="button" [disabled]="!files().length" (click)="submitIdentify()">Scan it</button>
            <button class="stake stake--quiet" type="button" (click)="actions.activeForm.set(null)">Cancel</button>
          </div>
        } @else if (form.kind === 'add-plant') {
          <label class="rz-field"><span>Nickname</span>
            <input type="text" [(ngModel)]="nickname" placeholder="Office Fig" autofocus /></label>
          <label class="rz-field"><span>Species (optional)</span>
            <input type="text" [(ngModel)]="species" placeholder="Ficus lyrata" /></label>
          <label class="rz-field"><span>Location (optional)</span>
            <input type="text" [(ngModel)]="location" placeholder="South window" /></label>
          <p class="rz-form__note">A new plant takes a free cell on the lattice. Nothing else moves.</p>
          <div class="btn-row">
            <button class="stake" type="button" [disabled]="!nickname().trim()" (click)="submitPlant()">Plant it</button>
            <button class="stake stake--quiet" type="button" (click)="actions.activeForm.set(null)">Cancel</button>
          </div>
        } @else if (form.kind === 'add-reminder' || form.kind === 'change-schedule') {
          <label class="rz-field"><span>Plant</span>
            <select [ngModel]="plantId()" (ngModelChange)="plantId.set(+$event)" [disabled]="form.kind === 'change-schedule'">
              @for (p of plants(); track p.id) { <option [value]="p.id">{{ p.nickname }}</option> }
            </select></label>
          <label class="rz-field"><span>Kind of care</span>
            <select [ngModel]="careType()" (ngModelChange)="careType.set($event)">
              @for (c of careOptions(); track c) { <option [value]="c">{{ label(c) }}</option> }
            </select></label>
          <label class="rz-field"><span>Repeat every (days)</span>
            <input type="number" min="1" [ngModel]="frequencyDays()" (ngModelChange)="frequencyDays.set(+$event)" /></label>
          <label class="rz-field"><span>First due</span>
            <input type="date" [ngModel]="firstDue()" (ngModelChange)="firstDue.set($event)" /></label>
          <p class="rz-form__note">{{ form.kind === 'change-schedule'
            ? 'A new schedule replaces the old reminder — nothing else is touched.'
            : 'A reminder belongs to one plant and one kind of care.' }}</p>
          <div class="btn-row">
            <button class="stake" type="button" [disabled]="!canSchedule()" (click)="submitSchedule(form)">{{ form.kind === 'change-schedule' ? 'Change it' : 'Set the reminder' }}</button>
            <button class="stake stake--quiet" type="button" (click)="actions.activeForm.set(null)">Cancel</button>
          </div>
        } @else if (form.kind === 'log-care') {
          <label class="rz-field"><span>Plant</span>
            <select [ngModel]="plantId()" (ngModelChange)="plantId.set(+$event)">
              @for (p of plants(); track p.id) { <option [value]="p.id">{{ p.nickname }}</option> }
            </select></label>
          <label class="rz-field"><span>Kind of care</span>
            <select [ngModel]="careType()" (ngModelChange)="careType.set($event)">
              @for (c of careOptions(); track c) { <option [value]="c">{{ label(c) }}</option> }
            </select></label>
          <label class="rz-field"><span>What you noticed (optional)</span>
            <textarea rows="2" [(ngModel)]="note" placeholder="Full soak, drained"></textarea></label>
          <p class="rz-form__note">Logging care completes the schedule it belongs to and writes the journal entry.</p>
          <div class="btn-row">
            <button class="stake" type="button" [disabled]="plantId() === null" (click)="submitLogCare(form)">Log it</button>
            <button class="stake stake--quiet" type="button" (click)="actions.activeForm.set(null)">Cancel</button>
          </div>
        } @else if (form.kind === 'start-treatment') {
          <label class="rz-field"><span>Plant</span>
            <select [ngModel]="plantId()" (ngModelChange)="plantId.set(+$event)">
              @for (p of plants(); track p.id) { <option [value]="p.id">{{ p.nickname }}</option> }
            </select></label>
          <label class="rz-field"><span>What is wrong</span>
            <input type="text" [(ngModel)]="disease" placeholder="Root rot" /></label>
          @if (scanId() === undefined) {
            <p class="rz-form__note">A treatment starts from a scan — identify this plant first.</p>
          }
          <div class="btn-row">
            <button class="stake" type="button" [disabled]="!disease().trim() || plantId() === null || scanId() === undefined" (click)="submitTreatment()">Start the course</button>
            <button class="stake stake--quiet" type="button" (click)="actions.activeForm.set(null)">Cancel</button>
          </div>
        } @else {
          <label class="rz-field"><span>Note for {{ form.plantName }}</span>
            <textarea rows="4" [(ngModel)]="note" placeholder="What did you notice?"></textarea></label>
          <div class="btn-row">
            <button class="stake" type="button" [disabled]="!note().trim()" (click)="submitNote(form.plantId)">Record note</button>
            <button class="stake stake--quiet" type="button" (click)="actions.activeForm.set(null)">Cancel</button>
          </div>
        }
      </section>
    }
  `,
  styles: [
    `
      .rz-form-scrim {
        position: fixed;
        inset: 0;
        z-index: 40;
        background: rgba(4, 6, 6, 0.55);
      }
      .rz-form {
        position: fixed;
        z-index: 41;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        width: min(26rem, 92vw);
        padding: var(--vs-gutter-card, 16px);
        display: grid;
        gap: var(--vs-gutter, 12px);
        background: var(--vs-sheet, #101312);
        border: var(--vs-hair, 1px) solid var(--vs-membrane-lit, rgba(255, 255, 255, 0.2));
        border-radius: var(--vs-corner-card, 5px);
        box-shadow: var(--vs-halo-lit, 0 10px 40px rgba(0, 0, 0, 0.6));
        color: var(--vs-ink);
      }
      .rz-field {
        display: grid;
        gap: 4px;
      }
      .rz-field span {
        font-family: var(--vs-face-label, monospace);
        font-size: var(--vs-rung-18, 0.72rem);
        letter-spacing: var(--vs-track-label, 0.12em);
        text-transform: uppercase;
        color: var(--vs-ink-faint);
      }
      .rz-field input,
      .rz-field select,
      .rz-field textarea {
        font: inherit;
        color: var(--vs-ink);
        background: var(--vs-well, rgba(255, 255, 255, 0.04));
        border: var(--vs-hair, 1px) solid var(--vs-membrane, rgba(255, 255, 255, 0.14));
        border-radius: var(--vs-corner-well, 2px);
        padding: 8px 10px;
      }
      .rz-field input:focus,
      .rz-field select:focus,
      .rz-field textarea:focus {
        outline: none;
        border-color: var(--vs-vital, #8fb26a);
      }
      .rz-form__note {
        margin: 0;
        font-size: var(--vs-rung-18, 0.72rem);
        color: var(--vs-ink-faint);
      }
    `,
  ],
})
export class StakeForm {
  protected readonly actions = inject(WorldActionsService);
  private readonly store = inject(WorldStore);
  private readonly settings = inject(SettingsStore);

  readonly nickname = signal('');
  readonly species = signal('');
  readonly location = signal('');
  readonly note = signal('');
  readonly files = signal<File[]>([]);
  readonly organ = signal('leaf');
  readonly context = signal('');
  readonly plantId = signal<number | null>(null);
  readonly careType = signal<CareType>('WATERING');
  readonly frequencyDays = signal(7);
  readonly firstDue = signal('');
  readonly disease = signal('');

  /** The plants this sheet can name - the board's own index. */
  protected readonly plants = computed(() => this.store.meta()?.plantsIndex ?? []);
  protected readonly careOptions = computed(() =>
    this.settings.settings().care.careTypes === 'four' ? CARE_TYPES.slice(0, 4) : CARE_TYPES,
  );
  /** A treatment is born from an identification - no scan, no course. */
  protected readonly scanId = computed(() => {
    const id = this.plantId();
    return id === null ? undefined : this.store.meta()?.scansByPlant[id];
  });
  protected readonly canSchedule = computed(
    () => this.plantId() !== null && this.frequencyDays() >= 1 && !!this.firstDue(),
  );

  /** The form identity this sheet was last seeded from (opening-scoped). */
  private seeded: ActiveForm | null = null;

  constructor() {
    // each OPENING seeds the sheet from what the stake knew — and only the opening:
    // a board reload while the sheet is open must never wipe what the reader typed
    effect(() => {
      const form = this.actions.activeForm();
      if (!form) {
        this.seeded = null;
        return;
      }
      if (form === this.seeded) return;
      this.seeded = form;
      if (form.kind === 'add-reminder' || form.kind === 'log-care' || form.kind === 'start-treatment') {
        this.plantId.set(form.plantId ?? this.plants()[0]?.id ?? null);
      }
      if (form.kind === 'change-schedule') this.plantId.set(form.plantId);
      if (form.kind === 'add-reminder' || form.kind === 'log-care' || form.kind === 'change-schedule') {
        this.careType.set(form.careType ?? 'WATERING');
      }
      this.frequencyDays.set(
        form.kind === 'change-schedule'
          ? form.frequencyDays
          : this.settings.settings().care.defaultFrequencyDays,
      );
      this.firstDue.set(new Date().toISOString().slice(0, 10));
      if (form.kind === 'start-treatment') this.disease.set('');
    });
  }

  protected label(c: string): string {
    return careLabel(c);
  }

  protected readonly title = computed(() => {
    const kind = this.actions.activeForm()?.kind;
    if (kind === 'identify') return 'Identify a plant';
    if (kind === 'add-plant') return 'Add a plant';
    if (kind === 'add-reminder') return 'Add a reminder';
    if (kind === 'change-schedule') return 'Change the schedule';
    if (kind === 'log-care') return 'Log care';
    if (kind === 'start-treatment') return 'Start a treatment plan';
    return 'Add a note';
  });

  protected onFiles(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.files.set(Array.from(input.files ?? []));
  }

  protected submitIdentify(): void {
    this.actions.identify(this.files(), this.organ(), this.context().trim() || undefined);
    this.files.set([]); this.context.set('');
  }

  protected submitPlant(): void {
    this.actions.createPlant({
      nickname: this.nickname().trim(),
      species: this.species().trim() || undefined,
      location: this.location().trim() || undefined,
    });
    this.nickname.set(''); this.species.set(''); this.location.set('');
  }

  protected submitSchedule(form: { kind: string; reminderId?: number }): void {
    const plantId = this.plantId();
    if (plantId === null) return;
    const req = {
      plantId,
      careType: this.careType(),
      frequencyDays: this.frequencyDays(),
      firstDueAt: new Date(`${this.firstDue()}T09:00:00`).toISOString(),
    };
    if (form.kind === 'change-schedule' && form.reminderId !== undefined) {
      this.actions.changeSchedule(form.reminderId, req);
      return;
    }
    this.actions.createReminder(req);
  }

  protected submitLogCare(form: { reminderId?: number }): void {
    const plantId = this.plantId();
    if (plantId === null) return;
    const notes = this.note().trim() || undefined;
    if (form.reminderId !== undefined) {
      this.actions.completeReminder(form.reminderId, notes, 'Care logged. The camera did not move.');
    } else {
      this.actions.careFor(plantId, this.careType(), 'Care logged. The camera did not move.', {
        notes,
        direct: true,
      });
    }
    this.note.set('');
  }

  protected submitTreatment(): void {
    const plantId = this.plantId();
    if (plantId === null) return;
    this.actions.startTreatment({
      plantId,
      diseaseName: this.disease().trim(),
      identificationId: this.scanId(),
    });
  }

  protected submitNote(plantId: number): void {
    this.actions.addNote(plantId, this.note().trim());
    this.note.set('');
  }
}
