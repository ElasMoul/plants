package com.plantpal.species.service.impl;

import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.dto.SpeciesSummaryDto;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.mapper.SpeciesMapper;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesEnrichmentService;
import com.plantpal.species.service.SpeciesService;
import com.plantpal.user.entity.AiModelPreference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpeciesServiceImpl implements SpeciesService {

  private static final Logger log = LoggerFactory.getLogger(SpeciesServiceImpl.class);
  private static final String ISSUES_DETECTED = "ISSUES_DETECTED";

  private final SpeciesRepository speciesRepository;
  private final SpeciesMapper speciesMapper;
  private final Optional<SpeciesEnrichmentService> speciesEnrichmentService;
  private final PlantRepository plantRepository;
  private final IdentificationRepository identificationRepository;

  public SpeciesServiceImpl(
      SpeciesRepository speciesRepository,
      SpeciesMapper speciesMapper,
      Optional<SpeciesEnrichmentService> speciesEnrichmentService,
      PlantRepository plantRepository,
      IdentificationRepository identificationRepository) {
    this.speciesRepository = speciesRepository;
    this.speciesMapper = speciesMapper;
    this.speciesEnrichmentService = speciesEnrichmentService;
    this.plantRepository = plantRepository;
    this.identificationRepository = identificationRepository;
  }

  @Override
  @Transactional
  public Species findOrCreate(
      String scientificName, String commonName, AiModelPreference preference) {
    return speciesRepository
        .findByScientificName(scientificName)
        .orElseGet(() -> createSpecies(scientificName, commonName, preference));
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
    Page<Long> speciesIdPage =
        plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
            userId, PlantStatus.ACTIVE, pageable);
    List<Long> speciesIds = speciesIdPage.getContent();
    if (speciesIds.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, speciesIdPage.getTotalElements());
    }

    List<Plant> plants =
        plantRepository.findAllByUserIdAndStatusAndSpeciesIdIn(
            userId, PlantStatus.ACTIVE, speciesIds);
    Map<Long, List<Plant>> plantsBySpeciesId =
        plants.stream().collect(Collectors.groupingBy(Plant::getSpeciesId));

    List<Long> plantIds = plants.stream().map(Plant::getId).toList();
    Map<Long, String> healthByPlantId =
        identificationRepository.findLatestPerPlant(plantIds).stream()
            .collect(Collectors.toMap(Identification::getPlantId, Identification::getHealthStatus));

    Map<Long, Species> speciesById =
        speciesRepository.findAllById(speciesIds).stream()
            .collect(Collectors.toMap(Species::getId, s -> s));

    List<SpeciesSummaryDto> content =
        speciesIds.stream()
            .map(
                speciesId ->
                    toSummary(
                        speciesById.get(speciesId),
                        plantsBySpeciesId.getOrDefault(speciesId, List.of()),
                        healthByPlantId))
            .filter(Objects::nonNull)
            .toList();

    return new PageImpl<>(content, pageable, speciesIdPage.getTotalElements());
  }

  private SpeciesSummaryDto toSummary(
      Species species, List<Plant> plants, Map<Long, String> healthByPlantId) {
    if (species == null) {
      return null;
    }
    long issueCount =
        plants.stream()
            .map(plant -> healthByPlantId.get(plant.getId()))
            .filter(ISSUES_DETECTED::equals)
            .count();
    String healthSummary = issueCount == 0 ? "All healthy" : issueCount + " issue(s)";
    return SpeciesSummaryDto.builder()
        .speciesId(species.getId())
        .scientificName(species.getScientificName())
        .commonName(species.getCommonName())
        .imageUrl(species.getImageUrl())
        .plantCount(plants.size())
        .healthSummary(healthSummary)
        .build();
  }

  private Species createSpecies(
      String scientificName, String commonName, AiModelPreference preference) {
    Species species =
        speciesRepository.save(
            Species.builder()
                .scientificName(scientificName)
                .commonName(commonName)
                .status(SpeciesStatus.ACTIVE)
                .build());
    log.info("Species created: id={}, scientificName={}", species.getId(), scientificName);

    Long speciesId = species.getId();
    speciesEnrichmentService.ifPresent(service -> service.enrich(speciesId, preference));

    return species;
  }
}
