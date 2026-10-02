import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SpeciesService } from './species.service';

describe('SpeciesService', () => {
  it('getSpeciesPhotos requests the species photos endpoint', () => {
    TestBed.configureTestingModule({
      providers: [SpeciesService, provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(SpeciesService).getSpeciesPhotos(7).subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/species/7/photos'));
    expect(req.request.method).toBe('GET');
    req.flush({ data: { plants: [] } });
    http.verify();
  });
});
