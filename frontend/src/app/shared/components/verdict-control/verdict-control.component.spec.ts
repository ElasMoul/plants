import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FR } from '../../i18n/fr';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { VerdictControlComponent } from './verdict-control.component';

describe('VerdictControlComponent', () => {
  let fixture: ComponentFixture<VerdictControlComponent>;
  let http: HttpTestingController;
  const el = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [VerdictControlComponent],
      imports: [FormsModule, TranslatePipe],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VerdictControlComponent);
    fixture.componentRef.setInput('identificationId', 7);
    fixture.componentRef.setInput('candidates', [{ scientificName: 'Ficus lyrata' }]);
    fixture.detectChanges();
  });

  it('renders the three options and no species input', () => {
    expect(el('verdict-RIGHT')).toBeTruthy();
    expect(el('verdict-WRONG')).toBeTruthy();
    expect(el('verdict-UNSURE')).toBeTruthy();
    expect(el('verdict-species')).toBeNull();
  });

  it('shows the species input with suggestions only for wrong and saves the verdict', () => {
    el('verdict-WRONG')!.click();
    const req = http.expectOne((r) => r.url.endsWith('/identifications/7/verdict'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ verdict: 'WRONG', correctSpecies: null });
    req.flush({ data: {} });
    fixture.detectChanges();
    expect(el('verdict-species')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('datalist option')).toHaveLength(1);
  });

  it('clears the species when switching away from wrong', () => {
    el('verdict-WRONG')!.click();
    http.expectOne(() => true).flush({ data: {} });
    fixture.componentInstance.species = 'Ficus lyrata';
    el('verdict-RIGHT')!.click();
    const req = http.expectOne(() => true);
    expect(req.request.body).toEqual({ verdict: 'RIGHT', correctSpecies: null });
    req.flush({ data: {} });
  });

  it('shows the saved verdict as selected', () => {
    fixture.componentRef.setInput('initialVerdict', 'UNSURE');
    fixture.detectChanges();
    expect(el('verdict-UNSURE')!.classList).toContain('selected');
  });

  it('has FR translations for every new text', () => {
    for (const key of [
      'Was this right?', 'Right', 'Wrong', 'Not sure',
      'What is the correct species? (optional)', 'Thanks for your feedback',
      'Could not save your answer. Please try again.', 'Identification feedback',
      'Could not load feedback counts.',
    ]) {
      expect(FR[key]).toBeTruthy();
    }
  });
});
