import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SpeciesDetailComponent } from './species-detail.component';

describe('SpeciesDetailComponent gallery', () => {
  let species: { getSpecies: jest.Mock; getSpeciesPhotos: jest.Mock };
  let component: SpeciesDetailComponent;

  const create = () => {
    component = new SpeciesDetailComponent(
      { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } as unknown as ActivatedRoute,
      species as never,
      { getPlants: jest.fn(() => of({ data: { content: [] } })) } as never,
      {} as never,
      {} as never,
      { open: jest.fn() } as never,
      {} as unknown as Router,
    );
  };

  beforeEach(() => {
    species = {
      getSpecies: jest.fn(() => of({ data: { id: 7, descriptionStatus: 'READY' } })),
      getSpeciesPhotos: jest.fn(),
    };
  });

  afterEach(() => component.ngOnDestroy());

  it('flattens photos from several plants and orders them oldest first', () => {
    species.getSpeciesPhotos.mockReturnValue(of({
      data: {
        speciesId: 7,
        plants: [
          { plantId: 1, nickname: 'A', photos: [
            { identificationId: 3, photoUrl: '/photos/c.jpg', dateTaken: '2026-03-01T00:00:00Z' },
            { identificationId: 1, photoUrl: '/photos/a.jpg', dateTaken: '2026-01-01T00:00:00Z' },
          ] },
          { plantId: 2, nickname: 'B', photos: [
            { identificationId: 2, photoUrl: '/photos/b.jpg', dateTaken: '2026-02-01T00:00:00Z' },
          ] },
        ],
      },
    }));
    create();
    component.ngOnInit();

    expect(component.photos.map(p => p.photoUrl)).toEqual(['/photos/a.jpg', '/photos/b.jpg', '/photos/c.jpg']);
    expect(component.photosLoading).toBe(false);
  });

  it('shows an empty list when the user has no photos', () => {
    species.getSpeciesPhotos.mockReturnValue(of({ data: { speciesId: 7, plants: [] } }));
    create();
    component.ngOnInit();

    expect(component.photos).toEqual([]);
    expect(component.photosLoading).toBe(false);
  });

  it('falls back to the empty state when the fetch fails', () => {
    species.getSpeciesPhotos.mockReturnValue(throwError(() => new Error('boom')));
    create();
    component.ngOnInit();

    expect(component.photos).toEqual([]);
    expect(component.photosLoading).toBe(false);
  });
});
