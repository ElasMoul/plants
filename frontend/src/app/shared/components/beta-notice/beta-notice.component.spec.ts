import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FR } from '../../i18n/fr';
import { AR } from '../../i18n/ar';
import { BetaNoticeComponent } from './beta-notice.component';

describe('BetaNoticeComponent', () => {
  let fixture: ComponentFixture<BetaNoticeComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ declarations: [BetaNoticeComponent] });
    fixture = TestBed.createComponent(BetaNoticeComponent);
    fixture.detectChanges();
  });

  it('renders the notice text with a stable test id', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('[data-testid="beta-notice"]');
    expect(el.textContent).toContain('This app is still in beta');
  });

  it('has FR and AR translations', () => {
    expect(FR['This app is still in beta']).toBeTruthy();
    expect(AR['This app is still in beta']).toBeTruthy();
  });
});
