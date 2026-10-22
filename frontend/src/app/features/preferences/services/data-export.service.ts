import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '@plantpal/shared-core';

export interface DataExport {
  id: string;
  expiresAt: string;
  sizeBytes: number;
  retentionHours: number;
}

/** Retention shown before the first request; the server's own value replaces it afterwards. */
export const DEFAULT_EXPORT_RETENTION_HOURS = 24;

@Injectable({ providedIn: 'root' })
export class DataExportService {
  private readonly baseUrl = `${environment.apiUrl}/users/me/export`;

  constructor(private readonly http: HttpClient) {}

  /** Builds the export, then fetches the ZIP through HttpClient so the JWT interceptor applies. */
  requestExport(): Observable<{ export: DataExport; blob: Blob }> {
    return this.http.post<ApiResponse<DataExport>>(this.baseUrl, null).pipe(
      map(res => res.data),
      switchMap(created =>
        this.http
          .get(`${this.baseUrl}/${created.id}`, { responseType: 'blob' })
          .pipe(map(blob => ({ export: created, blob }))),
      ),
    );
  }

  /** Hands the blob to the browser as a file download. */
  save(blob: Blob, filename = 'plantpal-export.zip'): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }
}
