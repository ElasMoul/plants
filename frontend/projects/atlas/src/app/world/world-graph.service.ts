import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL, ApiResponse, PageResponse } from '@plantpal/shared-core';
import { map, Observable } from 'rxjs';
import { forkJoin } from 'rxjs';
import { assembleWorld } from './world.assembly';
import { DashboardDto, PlantDto, SpeciesDto } from './world.dto';
import { WorldData } from './world.model';

/**
 * Assembles the world graph from the live backend, entirely client-side (no new
 * endpoint): one dashboard call plus the plants and species lists, mapped through
 * assembleWorld(). The store overlays this on the fixture when it resolves.
 */
@Injectable({ providedIn: 'root' })
export class WorldGraphService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  load(): Observable<WorldData> {
    const page = { params: { size: '50' } };
    return forkJoin({
      dashboard: this.http
        .get<ApiResponse<DashboardDto>>(`${this.base}/dashboard`)
        .pipe(map(r => r.data)),
      plants: this.http
        .get<ApiResponse<PageResponse<PlantDto>>>(`${this.base}/plants`, page)
        .pipe(map(r => r.data.content)),
      species: this.http
        .get<ApiResponse<PageResponse<SpeciesDto>>>(`${this.base}/species/mine`, page)
        .pipe(map(r => r.data.content)),
    }).pipe(map(sources => assembleWorld(sources)));
  }
}
