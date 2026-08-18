import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL, ApiResponse, AuthService, PageResponse } from '@plantpal/shared-core';
import { forkJoin, map, Observable } from 'rxjs';
import { assembleWorld } from './world.assembly';
import { IdentificationDto, PlantDto, SpeciesDto } from './world.dto';
import { WorldData } from './world.model';

/**
 * Assembles the world from the live backend — the mission's round-1 spine:
 * plants + species + identifications (the async family; a PENDING scan sets
 * hasPendingScan and the shell polls until it lands). No dashboard call — that
 * family is deferred by the coverage scope. Entirely client-side; no new
 * endpoint.
 */
@Injectable({ providedIn: 'root' })
export class WorldGraphService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly auth = inject(AuthService);

  load(): Observable<WorldData> {
    const page = { params: { size: '50' } };
    return forkJoin({
      plants: this.http
        .get<ApiResponse<PageResponse<PlantDto>>>(`${this.base}/plants`, page)
        .pipe(map(r => r.data.content)),
      species: this.http
        .get<ApiResponse<PageResponse<SpeciesDto>>>(`${this.base}/species/mine`, page)
        .pipe(map(r => r.data.content)),
      identifications: this.http
        .get<ApiResponse<PageResponse<IdentificationDto>>>(`${this.base}/identifications`, page)
        .pipe(map(r => r.data.content)),
    }).pipe(
      map(({ plants, species, identifications }) =>
        assembleWorld({ plants, species, identifications, user: this.auth.getCurrentUser() }),
      ),
    );
  }
}
