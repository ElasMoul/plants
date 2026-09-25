import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { IdentificationService } from './identification.service';

const BASE = `${environment.apiUrl}/identifications`;

describe('IdentificationService', () => {
  let service: IdentificationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [IdentificationService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(IdentificationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('analyze()', () => {
    it('posts each image and organ as its own multipart part, plus optional ids and context', () => {
      const a = new File(['a'], 'a.jpg');
      const b = new File(['b'], 'b.jpg');

      service.analyze([a, b], ['leaf', 'flower'], 7, 3, '  brown tips  ').subscribe();

      const req = http.expectOne(`${BASE}/analyze`);
      expect(req.request.method).toBe('POST');
      const form = req.request.body as FormData;
      expect(form.getAll('images')).toHaveLength(2);
      expect(form.getAll('organs')).toEqual(['leaf', 'flower']);
      expect(form.get('plantId')).toBe('7');
      expect(form.get('speciesId')).toBe('3');
      expect(form.get('userContext')).toBe('brown tips');
      req.flush({ data: { identificationId: 1 } });
    });

    it('omits absent ids and a blank user context', () => {
      service.analyze([new File(['a'], 'a.jpg')], ['auto'], undefined, undefined, '   ').subscribe();

      const form = http.expectOne(`${BASE}/analyze`).request.body as FormData;
      expect(form.has('plantId')).toBe(false);
      expect(form.has('speciesId')).toBe(false);
      expect(form.has('userContext')).toBe(false);
    });
  });

  describe('pollUntilComplete()', () => {
    const reply = (status: string, annotationStatus = 'COMPLETED', candidateStatus = 'COMPLETED') => ({
      data: { id: 9, status, annotationStatus, candidateStatus },
    });

    it('stays silent while PENDING, then emits until enrichment settles and completes', fakeAsync(() => {
      const seen: string[] = [];
      let completed = false;
      service.pollUntilComplete(9).subscribe({
        next: r => seen.push(`${r.status}/${r.annotationStatus}`),
        complete: () => (completed = true),
      });

      http.expectOne(`${BASE}/9`).flush(reply('PENDING', 'PENDING', 'PENDING'));
      tick(3000);
      http.expectOne(`${BASE}/9`).flush(reply('COMPLETED', 'PENDING'));
      tick(3000);
      http.expectOne(`${BASE}/9`).flush(reply('COMPLETED'));

      expect(seen).toEqual(['COMPLETED/PENDING', 'COMPLETED/COMPLETED']);
      expect(completed).toBe(true);
    }));

    it('emits a FAILED scan (callers must treat it as a failure)', fakeAsync(() => {
      const seen: string[] = [];
      service.pollUntilComplete(9).subscribe(r => seen.push(r.status));

      http.expectOne(`${BASE}/9`).flush(reply('FAILED', 'SKIPPED', 'SKIPPED'));

      expect(seen).toEqual(['FAILED']);
    }));

    it('errors when no settled result arrives within the timeout', fakeAsync(() => {
      let error: unknown;
      service.pollUntilComplete(9).subscribe({ error: e => (error = e) });

      for (let elapsed = 0; elapsed <= 30000; elapsed += 3000) {
        http.expectOne(`${BASE}/9`).flush(reply('PENDING', 'PENDING', 'PENDING'));
        tick(3000);
      }
      tick(2000);

      expect(error).toBeTruthy();
      discardPeriodicTasks();
    }));
  });

  describe('endpoints', () => {
    it('hits the matching backend routes with the expected bodies and params', () => {
      service.getById(4).subscribe();
      http.expectOne(`${BASE}/4`).flush({ data: {} });

      service.retryIdentification(4).subscribe();
      expect(http.expectOne(`${BASE}/4/retry`).request.method).toBe('POST');

      service.getUserIdentifications(2, 5).subscribe();
      http.expectOne(`${BASE}?page=2&size=5`).flush({ data: {} });

      service.getPlantIdentifications(8).subscribe();
      http.expectOne(`${BASE}/plant/8?page=0&size=3`).flush({ data: {} });

      let advice: unknown;
      service.getCureAdvice(4, 'Rust', 'Rosa').subscribe(a => (advice = a));
      const cure = http.expectOne(`${BASE}/4/cure-advice`);
      expect(cure.request.body).toEqual({ regionLabel: 'Rust', species: 'Rosa' });
      cure.flush({ data: { advice: 'Spray', actionPlan: null } });
      expect(advice).toEqual({ advice: 'Spray', actionPlan: null });

      service.addCareCard(4, 'Rust', 'Spray').subscribe();
      expect(http.expectOne(`${BASE}/4/care-plan/cards`).request.body).toEqual({
        regionLabel: 'Rust',
        adviceText: 'Spray',
        actionPlan: null,
        reasoningModelUsed: null,
      });

      service.getSpeciesMatch(4).subscribe();
      http.expectOne(`${BASE}/4/species-match`).flush({ data: {} });

      service.resolveSpecies(4, true).subscribe();
      expect(http.expectOne(`${BASE}/4/resolve-species`).request.body).toEqual({
        confirmed: true,
        chosenScientificName: null,
      });

      service.getPlantMatch(4).subscribe();
      http.expectOne(`${BASE}/4/plant-match`).flush({ data: {} });

      service.resolvePlant(4, null).subscribe();
      expect(http.expectOne(`${BASE}/4/resolve-plant`).request.body).toEqual({ plantId: null });
    });
  });
});
