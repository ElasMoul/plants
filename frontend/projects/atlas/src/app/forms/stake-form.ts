import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WorldActionsService } from '../world/world-actions.service';

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
        @if (form.kind === 'add-plant') {
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
      .rz-field textarea {
        font: inherit;
        color: var(--vs-ink);
        background: var(--vs-well, rgba(255, 255, 255, 0.04));
        border: var(--vs-hair, 1px) solid var(--vs-membrane, rgba(255, 255, 255, 0.14));
        border-radius: var(--vs-corner-well, 2px);
        padding: 8px 10px;
      }
      .rz-field input:focus,
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

  readonly nickname = signal('');
  readonly species = signal('');
  readonly location = signal('');
  readonly note = signal('');

  protected readonly title = computed(() =>
    this.actions.activeForm()?.kind === 'add-plant' ? 'Add a plant' : 'Add a note',
  );

  protected submitPlant(): void {
    this.actions.createPlant({
      nickname: this.nickname().trim(),
      species: this.species().trim() || undefined,
      location: this.location().trim() || undefined,
    });
    this.nickname.set(''); this.species.set(''); this.location.set('');
  }

  protected submitNote(plantId: number): void {
    this.actions.addNote(plantId, this.note().trim());
    this.note.set('');
  }
}
