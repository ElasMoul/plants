package com.plantpal.species.service;

/**
 * Fetches description/careOverview/imageUrl for a newly created Species row. Implemented in T6.4 —
 * no implementation exists yet, so {@code SpeciesServiceImpl.findOrCreate} injects this as an
 * {@code Optional} dependency and is a no-op until an implementation bean is registered.
 */
public interface SpeciesEnrichmentService {

  void enrich(Long speciesId);
}
