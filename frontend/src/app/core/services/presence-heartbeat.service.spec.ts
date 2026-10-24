import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DOCUMENT } from '@angular/common';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { API_BASE_URL, AuthService } from '@plantpal/shared-core';

import { HEARTBEAT_INTERVAL_MS, PresenceHeartbeatService } from './presence-heartbeat.service';

describe('PresenceHeartbeatService', () => {
  let service: PresenceHeartbeatService;
  let http: HttpTestingController;
  let loggedIn: boolean;
  let visibility: DocumentVisibilityState;
  let doc: Document;

  const sent = () => http.match('/api/v1/presence/heartbeat');

  beforeEach(() => {
    loggedIn = true;
    visibility = 'visible';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        { provide: AuthService, useValue: { isLoggedIn: () => loggedIn } },
      ],
    });
    doc = TestBed.inject(DOCUMENT);
    jest.spyOn(doc, 'visibilityState', 'get').mockImplementation(() => visibility);
    service = TestBed.inject(PresenceHeartbeatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => service.stop());

  it('beats immediately and then once per interval', fakeAsync(() => {
    service.start();
    tick(0);
    expect(sent()).toHaveLength(1);
    tick(HEARTBEAT_INTERVAL_MS);
    expect(sent()).toHaveLength(1);
    tick(HEARTBEAT_INTERVAL_MS);
    expect(sent()).toHaveLength(1);
    service.stop();
  }));

  it('pauses while hidden and beats at once on becoming visible', fakeAsync(() => {
    visibility = 'hidden';
    service.start();
    tick(HEARTBEAT_INTERVAL_MS * 2);
    expect(sent()).toHaveLength(0);
    visibility = 'visible';
    doc.dispatchEvent(new Event('visibilitychange'));
    expect(sent()).toHaveLength(1);
    service.stop();
  }));

  it('sends nothing once the user is signed out', fakeAsync(() => {
    service.start();
    tick(0);
    sent();
    loggedIn = false;
    tick(HEARTBEAT_INTERVAL_MS * 3);
    expect(sent()).toHaveLength(0);
    service.stop();
  }));

  it('stop() ends the timer', fakeAsync(() => {
    service.start();
    tick(0);
    sent();
    service.stop();
    tick(HEARTBEAT_INTERVAL_MS * 2);
    expect(sent()).toHaveLength(0);
  }));
});
