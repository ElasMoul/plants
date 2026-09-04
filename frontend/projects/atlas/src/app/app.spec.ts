import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { App } from './app';
import { AuthService } from '@plantpal/shared-core';
import { appConfigFor } from './app.config';
import { provideMockModeOff } from './core/mock-mode';
import { MockAuthService } from './mock/mock-auth.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideMockModeOff(),
        provideHttpClientTesting(),
        ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should expose the atlas title', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance.title()).toBe('atlas');
  });

  it('resolves AuthService to MockAuthService when the mode is enabled', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: appConfigFor({ enabled: true, scenario: 'garden', latencyMs: 0 }).providers,
    });
    const auth = TestBed.inject(AuthService);
    expect(auth).toBeInstanceOf(MockAuthService);
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.getCurrentUser()!.firstName).toBe('Sam');
  });
});
