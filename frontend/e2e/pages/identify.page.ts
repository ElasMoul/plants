import { Page } from '@playwright/test';

export class IdentifyPage {
  constructor(private page: Page) {}

  async navigate() {
    await this.page.goto('/identify');
  }

  async uploadPhoto(filePath: string) {
    await this.page.getByTestId('photo-upload-input').setInputFiles(filePath);
  }

  async clickAnalyze() {
    await this.page.getByTestId('analyze-btn').click();
  }

  async waitForResult(timeout = 30000) {
    await this.page.getByTestId('identification-result').waitFor({ timeout });
  }
}
