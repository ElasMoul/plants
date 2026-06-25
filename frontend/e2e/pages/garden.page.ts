import { Page } from '@playwright/test';

export class GardenPage {
  constructor(private page: Page) {}

  async navigate() {
    await this.page.goto('/garden');
  }

  async clickAddPlant() {
    await this.page.getByTestId('add-plant-btn').click();
  }

  async fillPlantForm(nickname: string) {
    await this.page.getByTestId('plant-nickname-input').fill(nickname);
  }

  async submitPlantForm() {
    await this.page.getByTestId('plant-form-submit').click();
  }

  async getPlantCards() {
    return this.page.getByTestId('plant-card').all();
  }
}
