package com.plantpal.species.service.impl;

import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.dto.SpeciesSummaryDto;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.mapper.SpeciesMapper;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesEnrichmentService;
import com.plantpal.species.service.SpeciesService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpeciesServiceImpl implements SpeciesService {

  private static final Logger log = LoggerFactory.getLogger(SpeciesServiceImpl.class);

  private final SpeciesRepository speciesRepository;
  private final SpeciesMapper speciesMapper;
  private final Optional<SpeciesEnrichmentService> speciesEnrichmentService;

  public SpeciesServiceImpl(
      SpeciesRepository speciesRepository,
      SpeciesMapper speciesMapper,
      Optional<SpeciesEnrichmentService> speciesEnrichmentService) {
    this.speciesRepository = speciesRepository;
    this.speciesMapper = speciesMapper;
    this.speciesEnrichmentService = speciesEnrichmentService;
  }

  @Override
  @Transactional
  public Species findOrCreate(String scientificName, String commonName) {
    return speciesRepository
        .findByScientificName(scientificName)
        .orElseGet(() -> createSpecies(scientificName, commonName));
  }

  @Override
  @Transactional(readOnly = true)
  public SpeciesResponse getSpecies(Long id) {
    Species species =
        speciesRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Species", id));
    return speciesMapper.toResponse(species);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SpeciesSummaryDto> getUserSpecies(Long userId, Pageable pageable) {
    // TODO(T6.3): Plant has no speciesId column yet (migration 017 adds plants.species_id).
    // Once it lands, group the caller's ACTIVE plants by speciesId here, computing plantCount
    // and healthSummary via IdentificationRepository.findLatestPerPlant() — same batch-fetch
    // pattern as PlantServiceImpl.enrichWithHealthAndWater(). Plants with speciesId IS NULL
    // (legacy, pre-Phase-6) must stay excluded.
    log.warn("getUserSpecies() is a stub pending T6.3 (plants.species_id): userId={}", userId);
    return Page.empty(pageable);
  }

  private Species createSpecies(String scientificName, String commonName) {
    Species species =
        speciesRepository.save(
            Species.builder()
                .scientificName(scientificName)
                .commonName(commonName)
                .status(SpeciesStatus.ACTIVE)
                .build());
    log.info("Species created: id={}, scientificName={}", species.getId(), scientificName);

    Long speciesId = species.getId();
    speciesEnrichmentService.ifPresent(service -> service.enrich(speciesId));

    return species;
  }
}
