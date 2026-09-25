import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SwPush } from '@angular/service-worker';
import { environment } from '../../environments/environment';
import { PushNotificationService } from '../core/services/push-notification.service';
import { ChatService } from './chat/services/chat.service';
import { DashboardService } from './dashboard/services/dashboard.service';
import { PlantService } from './plant/services/plant.service';
import { TreatmentService } from './plant/services/treatment.service';
import { PlantNetConfigService } from './preferences/services/plantnet-config.service';
import { CareLogService } from './reminder/services/care-log.service';
import { ReminderService } from './reminder/services/reminder.service';
import { TreatmentPlanService } from './reminder/services/treatment-plan.service';
import { SpeciesService } from './species/services/species.service';

/**
 * Pins every feature service's request (method, URL, body, params) to the backend route it must
 * hit — the routes below are the ones mapped by the Spring controllers, so a rename on either side
 * fails here instead of silently 404-ing in the browser.
 */
const API = environment.apiUrl;

describe('feature services ↔ backend API contract', () => {
  let http: HttpTestingController;
  const swPush = { requestSubscription: jest.fn() };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ChatService,
        DashboardService,
        PlantService,
        TreatmentService,
        PlantNetConfigService,
        CareLogService,
        ReminderService,
        TreatmentPlanService,
        SpeciesService,
        { provide: SwPush, useValue: swPush },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const expect1 = (method: string, url: string) => {
    const req = http.expectOne(r => r.urlWithParams === url);
    expect(req.request.method).toBe(method);
    return req;
  };

  it('PlantService', () => {
    const svc = TestBed.inject(PlantService);
    svc.getPlants().subscribe();
    expect1('GET', `${API}/plants?page=0&size=20&sort=createdAt,desc`).flush({});
    svc.getPlants(1, 5, 9).subscribe();
    expect1('GET', `${API}/plants?page=1&size=5&sort=createdAt,desc&speciesId=9`).flush({});
    svc.getPlant(3).subscribe();
    expect1('GET', `${API}/plants/3`).flush({});
    svc.createPlant({ nickname: 'Monty' } as never).subscribe();
    expect(expect1('POST', `${API}/plants`).request.body).toEqual({ nickname: 'Monty' });
    svc.updatePlant(3, { nickname: 'M' } as never).subscribe();
    expect1('PUT', `${API}/plants/3`);
    svc.archivePlant(3).subscribe();
    expect1('DELETE', `${API}/plants/3`);
    svc.saveFromIdentification({ identificationId: 4 } as never).subscribe();
    expect1('POST', `${API}/plants/from-identification`);
  });

  it('TreatmentService', () => {
    const svc = TestBed.inject(TreatmentService);
    svc.createTreatment(1, 2, 'Rust').subscribe();
    expect(expect1('POST', `${API}/treatments`).request.body).toEqual({
      plantId: 1,
      identificationId: 2,
      diseaseName: 'Rust',
    });
    svc.craftPlan(5).subscribe();
    expect1('POST', `${API}/treatments/5/craft-plan`);
    svc.getTreatment(5).subscribe();
    expect1('GET', `${API}/treatments/5`);
    svc.getActiveTreatments(1).subscribe();
    expect1('GET', `${API}/plants/1/active-treatments`);
    svc.completeTreatment(5).subscribe();
    expect1('PATCH', `${API}/treatments/5/complete`);
    svc.regenerateDescription(5).subscribe();
    expect1('POST', `${API}/treatments/5/regenerate-description`);
  });

  it('Reminder, CareLog and TreatmentPlan services', () => {
    const reminders = TestBed.inject(ReminderService);
    reminders.getReminders().subscribe();
    expect1('GET', `${API}/reminders`);
    reminders.createReminder({ plantId: 1 } as never).subscribe();
    expect1('POST', `${API}/reminders`);
    reminders.markCareDone(7).subscribe();
    expect(expect1('POST', `${API}/care/done`).request.body).toEqual({ reminderId: 7 });

    TestBed.inject(CareLogService).getPlantCareLogs(1).subscribe();
    expect1('GET', `${API}/care/plant/1?page=0&size=20`);

    const plans = TestBed.inject(TreatmentPlanService);
    plans.createFromActionPlan({ plantId: 1 } as never).subscribe();
    expect1('POST', `${API}/treatment-plans`);
    plans.getTreatmentPlan(8).subscribe();
    expect1('GET', `${API}/treatment-plans/8`);
  });

  it('SpeciesService and DashboardService', () => {
    const species = TestBed.inject(SpeciesService);
    species.getMySpecies().subscribe();
    expect1('GET', `${API}/species/mine?page=0&size=20`);
    species.getSpecies(4).subscribe();
    expect1('GET', `${API}/species/4`);
    species.regenerateDescription(4).subscribe();
    expect1('POST', `${API}/species/4/regenerate-description`);

    TestBed.inject(DashboardService).getDashboard().subscribe();
    expect1('GET', `${API}/dashboard`);
  });

  it('PlantNetConfigService unwraps data and tolerates a missing payload', () => {
    const svc = TestBed.inject(PlantNetConfigService);
    let projects: unknown;
    let languages: unknown;
    let quota: unknown = 'unset';
    svc.getProjects('fr').subscribe(p => (projects = p));
    expect1('GET', `${API}/plantnet/projects?lang=fr`).flush({ data: null });
    svc.getLanguages().subscribe(l => (languages = l));
    expect1('GET', `${API}/plantnet/languages`).flush({ data: ['en', 'fr'] });
    svc.getQuota().subscribe(q => (quota = q));
    expect1('GET', `${API}/plantnet/quota`).flush({});

    expect(projects).toEqual([]);
    expect(languages).toEqual(['en', 'fr']);
    expect(quota).toBeNull();
  });

  it('ChatService sends language, plant and non-empty history only', () => {
    const svc = TestBed.inject(ChatService);
    svc.sendMessage('hi').subscribe();
    const plain = expect1('POST', `${API}/chat`).request.body;
    expect(plain.message).toBe('hi');
    expect(plain.language).toBeTruthy();
    expect(plain).not.toHaveProperty('plantId');
    expect(plain).not.toHaveProperty('history');

    const history = [{ role: 'user', content: 'earlier' }];
    svc.sendMessageStream('hi', 3, history as never).subscribe();
    const stream = expect1('POST', `${API}/chat/stream`);
    expect(stream.request.responseType).toBe('text');
    expect(stream.request.reportProgress).toBe(true);
    expect(stream.request.body.plantId).toBe(3);
    expect(stream.request.body.history).toEqual(history);
  });

  it('PushNotificationService posts the browser subscription keys', async () => {
    swPush.requestSubscription.mockResolvedValue({
      toJSON: () => ({ endpoint: 'https://push.example/1', keys: { p256dh: 'pk', auth: 'ak' } }),
    });
    const svc = TestBed.inject(PushNotificationService);

    const done = new Promise<void>(resolve => svc.subscribeToNotifications().subscribe(() => resolve()));
    await Promise.resolve();
    await Promise.resolve();
    const req = expect1('POST', `${API}/notifications/subscribe`);
    expect(req.request.body).toEqual({
      endpoint: 'https://push.example/1',
      keyP256dh: 'pk',
      keyAuth: 'ak',
    });
    req.flush({});
    await done;
  });
});
