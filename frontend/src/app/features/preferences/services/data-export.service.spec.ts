import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DataExportService } from './data-export.service';

describe('DataExportService', () => {
  let service: DataExportService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DataExportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('POSTs the request, then downloads the ZIP as a blob', () => {
    let result: { blob: Blob } | undefined;
    service.requestExport().subscribe(r => (result = r));

    const post = http.expectOne('/api/v1/users/me/export');
    expect(post.request.method).toBe('POST');
    post.flush({
      success: true,
      data: { id: 'abc', expiresAt: 'x', sizeBytes: 3, retentionHours: 24 },
    });

    const get = http.expectOne('/api/v1/users/me/export/abc');
    expect(get.request.method).toBe('GET');
    expect(get.request.responseType).toBe('blob');
    get.flush(new Blob(['zip']));

    expect(result?.blob.size).toBe(3);
  });

  it('does not download anything when the request is rate limited', () => {
    let status = 0;
    service.requestExport().subscribe({ error: e => (status = e.status) });
    http.expectOne('/api/v1/users/me/export').flush({}, { status: 429, statusText: 'Too Many' });
    expect(status).toBe(429);
  });
});
