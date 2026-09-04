import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { provideMockModeOff } from '../core/mock-mode';
import { SettingsStore } from '../settings/settings.store';
import { WorldActionsService } from '../world/world-actions.service';
import { StakeForm } from './stake-form';

describe('StakeForm — the ask sheet (C2)', () => {
  let fixture: ComponentFixture<StakeForm>;
  let actions: WorldActionsService;
  let settings: SettingsStore;
  let http: HttpTestingController;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const textarea = (): HTMLTextAreaElement =>
    el().querySelector('textarea') as HTMLTextAreaElement;
  const stake = (label: string): HTMLButtonElement | undefined =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('.stake')).find(
      b => b.textContent?.trim() === label,
    );

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [StakeForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMockModeOff(),
        ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(StakeForm);
    actions = TestBed.inject(WorldActionsService);
    settings = TestBed.inject(SettingsStore);
    settings.set('ai.chatTransport', 'buffered');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function open(over: Record<string, unknown> = {}): void {
    actions.activeForm.set({ kind: 'ask', threadKey: 'garden', ...over } as never);
    fixture.detectChanges();
  }

  it('is the one in-world form — a single dialog, never a second one', () => {
    open();
    expect(el().querySelectorAll('[role="dialog"]').length).toBe(1);
    expect(el().querySelector('h3.sec')?.textContent).toBe('Ask PlantPal');
  });

  it('caps the question at the server’s own two thousand characters', () => {
    open();
    expect(textarea().getAttribute('maxlength')).toBe('2000');
    expect(el().textContent).not.toContain('characters left');
    textarea().value = 'x'.repeat(1900);
    textarea().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el().textContent).toContain('100 characters left');
  });

  it('asks about the plant it was opened from, and can be turned on the garden', () => {
    open({ threadKey: 'plant:1', plantId: 1, plantName: 'Office Fig' });
    expect(el().textContent).toContain('Asking about Office Fig.');
    const toggle = Array.from(el().querySelectorAll<HTMLButtonElement>('.stake--quiet')).find(b =>
      /whole garden instead/.test(b.textContent ?? ''),
    );
    toggle?.click();
    fixture.detectChanges();
    expect(el().textContent).toContain('Asking about the whole garden.');
  });

  it('sends the question, and never before "Ask it" is pressed', () => {
    open({ threadKey: 'plant:1', plantId: 1, plantName: 'Office Fig' });
    http.verify();
    expect(stake('Ask it')?.disabled).toBe(true);
    textarea().value = 'why are the low leaves going?';
    textarea().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    stake('Ask it')?.click();
    const req = http.expectOne('/api/v1/chat');
    expect(req.request.body).toEqual({
      message: 'why are the low leaves going?',
      plantId: 1,
      history: [],
    });
    req.flush({ success: true, message: '', timestamp: '', data: { reply: 'draught, most likely' } });
    expect(actions.activeForm()).toBeNull();
  });

  it('offers a chooser when the reader asked to pick the plant every time', () => {
    settings.set('ai.chatPlantContext', 'ask');
    open();
    expect(el().querySelector('select')).not.toBeNull();
  });
});
