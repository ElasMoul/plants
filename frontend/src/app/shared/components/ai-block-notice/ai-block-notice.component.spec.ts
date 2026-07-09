import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AiBlockNoticeComponent } from './ai-block-notice.component';

describe('AiBlockNoticeComponent', () => {
  let component: AiBlockNoticeComponent;
  let fixture: ComponentFixture<AiBlockNoticeComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AiBlockNoticeComponent],
      schemas: [NO_ERRORS_SCHEMA],
    });
    fixture = TestBed.createComponent(AiBlockNoticeComponent);
    component = fixture.componentInstance;
  });

  it('renders the title and guidance', () => {
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Daily AI limit reached');
    expect(text).toContain('try again tomorrow');
  });

  it('shows a real backend reason string', () => {
    component.reason = 'app plantpal hit its daily ceiling of 100 requests';
    expect(component.showReason).toBe(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('daily ceiling of 100 requests');
  });

  it('suppresses the raw AI_LIMIT_REACHED tag (not user-facing prose)', () => {
    component.reason = 'AI_LIMIT_REACHED';
    expect(component.showReason).toBe(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('AI_LIMIT_REACHED');
  });

  it('shows no reason line when reason is null or blank', () => {
    component.reason = null;
    expect(component.showReason).toBe(false);
    component.reason = '   ';
    expect(component.showReason).toBe(false);
  });
});
