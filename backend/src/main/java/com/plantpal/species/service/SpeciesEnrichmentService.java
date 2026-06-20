package com.plantpal.species.service;

/**
 * Fetches description/careOverview/imageUrl for a newly created Species row. {@code
 * SpeciesServiceImpl.findOrCreate} injects this as an {@code Optional} dependency — still safe to
 * leave unimplemented in tests/contexts where no bean is registered (degrades to a no-op).
 */
public interface SpeciesEnrichmentService {

  void enrich(Long speciesId);
}
