import { Component } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-ai-test',
  template: `
    <div class="ai-test-container">
      <mat-card class="ai-test-card">
        <mat-card-header>
          <mat-icon mat-card-avatar color="primary">psychology</mat-icon>
          <mat-card-title>AI Connection Test</mat-card-title>
          <mat-card-subtitle>POST to /api/test/ai — no auth required</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Prompt</mat-label>
            <textarea
              matInput
              [(ngModel)]="prompt"
              placeholder="hi there am mouloud, whats my nationality?"
              rows="3"
              (keydown.control.enter)="send()"
            ></textarea>
            <mat-hint>Ctrl+Enter to send</mat-hint>
          </mat-form-field>

          <div class="actions">
            <button
              mat-raised-button
              color="primary"
              (click)="send()"
              [disabled]="loading || !prompt.trim()"
            >
              <mat-icon>send</mat-icon>
              Send
            </button>
            <button mat-stroked-button (click)="reset()" [disabled]="loading">
              <mat-icon>refresh</mat-icon>
              Reset
            </button>
          </div>

          <mat-progress-bar *ngIf="loading" mode="indeterminate" class="progress"></mat-progress-bar>

          <div *ngIf="response" class="response-box">
            <div class="response-label">
              <mat-icon color="primary">smart_toy</mat-icon>
              <span>AI Response</span>
            </div>
            <p class="response-text">{{ response }}</p>
          </div>

          <mat-card *ngIf="errorMsg" class="error-card">
            <mat-card-content>
              <mat-icon color="warn">error_outline</mat-icon>
              {{ errorMsg }}
            </mat-card-content>
          </mat-card>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .ai-test-container {
      display: flex;
      justify-content: center;
      padding: 40px 16px;
    }
    .ai-test-card {
      width: 100%;
      max-width: 720px;
    }
    mat-card-content {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding-top: 16px;
    }
    .actions {
      display: flex;
      gap: 12px;
    }
    .progress {
      border-radius: 4px;
    }
    .response-box {
      background: #f5f5f5;
      border-left: 4px solid #3f51b5;
      border-radius: 4px;
      padding: 16px;
    }
    .response-label {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
      margin-bottom: 8px;
    }
    .response-text {
      margin: 0;
      white-space: pre-wrap;
      line-height: 1.6;
    }
    .error-card {
      background: #fff3f3;
      mat-card-content {
        display: flex;
        align-items: center;
        gap: 8px;
        color: #c62828;
      }
    }
  `],
})
export class AiTestComponent {
  prompt = 'hi there am mouloud, whats my nationality?';
  response = '';
  errorMsg = '';
  loading = false;

  constructor(private http: HttpClient) {}

  send(): void {
    if (!this.prompt.trim() || this.loading) return;
    this.loading = true;
    this.response = '';
    this.errorMsg = '';

    const params = new HttpParams().set('prompt', this.prompt.trim());

    this.http.get(`${environment.apiUrl}/test/ai`, { params, responseType: 'text' }).subscribe({
      next: (res) => {
        this.response = res;
        this.loading = false;
      },
      error: (err) => {
        this.errorMsg = err?.error || `Error ${err.status}: ${err.statusText}`;
        this.loading = false;
      },
    });
  }

  reset(): void {
    this.prompt = '';
    this.response = '';
    this.errorMsg = '';
  }
}
