import { Page } from '@playwright/test';

export class AuthPage {
  constructor(private page: Page) {}

  async navigateToLogin() {
    await this.page.goto('/login');
  }

  async navigateToRegister() {
    await this.page.goto('/register');
  }

  async login(email: string, password: string) {
    await this.page.getByTestId('login-email').fill(email);
    await this.page.getByTestId('login-password').fill(password);
    await this.page.getByTestId('login-submit').click();
  }

  async register(email: string, password: string, name: string) {
    await this.page.getByTestId('register-name').fill(name);
    await this.page.getByTestId('register-email').fill(email);
    await this.page.getByTestId('register-password').fill(password);
    await this.page.getByTestId('register-submit').click();
  }
}
