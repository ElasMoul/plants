package com.plantpal.species.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.dto.CareCardDto;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.entity.GenerationStatus;
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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SpeciesServiceImpl implements SpeciesService {

  private static final Logger log = LoggerFactory.getLogger(SpeciesServiceImpl.class);
  private static final String ISSUES_DETECTED = "ISSUES_DETECTED";
  private static final String SPECIES_CACHE = "species";

  private final SpeciesRepository speciesRepository;
  private final SpeciesMapper speciesMapper;
  private final Optional<SpeciesEnrichmentService> speciesEnrichmentService;
  private final PlantRepository plantRepository;
  private final IdentificationRepository identificationRepository;
  private final ObjectMapper objectMapper;

  public SpeciesServiceImpl(
      SpeciesRepository speciesRepository,
      SpeciesMapper speciesMapper,
      Optional<SpeciesEnrichmentService> speciesEnrichmentService,
      PlantRepository plantRepository,
      IdentificationRepository identificationRepository,
      ObjectMapper objectMapper) {
    this.speciesRepository = speciesRepository;
    this.speciesMapper = speciesMapper;
    this.speciesEnrichmentService = speciesEnrichmentService;
    this.plantRepository = plantRepository;
    this.identificationRepository = identificationRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  @CacheEvict(value = SPECIES_CACHE, key = "#result.id")
  public Species findOrCreate(
      String scientificName, String commonName, AiModelPreference preference) {
    return findOrCreate(scientificName, commonName, preference, null, null, null);
  }

  @Override
  @Transactional
  @CacheEvict(value = SPECIES_CACHE, key = "#result.id")
  public Species findOrCreate(
      String scientificName,
      String commonName,
      AiModelPreference preference,
      String gbifId,
      String powoId,
      String iucnCategory) {
    return findOrCreate(
        scientificName,
        commonName,
        preference,
        gbifId,
        powoId,
        iucnCategory,
        null,
        null,
        null,
        null,
        null);
  }

  @Override
  @Transactional
  @CacheEvict(value = SPECIES_CACHE, key = "#result.id")
  public Species findOrCreate(
      String scientificName,
      String commonName,
      AiModelPreference preference,
      String gbifId,
      String powoId,
      String iucnCategory,
      String family,
      String genus,
      String imageUrl,
      String imageAttribution,
      String imageLicense) {
    return speciesRepository
        .findByScientificName(scientificName)
        .map(
            existing ->
                patchFactualFields(
                    existing,
                    gbifId,
                    powoId,
                    iucnCategory,
                    family,
                    genus,
                    imageUrl,
                    imageAttribution,
                    imageLicense))
        .orElseGet(
            () ->
                createSpecies(
                    scientificName,
                    commonName,
                    preference,
                    gbifId,
                    powoId,
                    iucnCategory,
                    family,
                    genus,
                    imageUrl,
                    imageAttribution,
                    imageLicense));
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = SPECIES_CACHE, key = "#id")
  public SpeciesResponse getSpecies(Long id) {
    Species species =
        speciesRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Species", id));
    return speciesMapper.toResponse(species).toBuilder()
        .careCards(parseCareCards(species.getCareCards()))
        .build();
  }

  // Never throws — same defensive-parse philosophy as IdentificationServiceImpl.parseCarePlan().
  // Null input (enrichment hasn't run / produced no cards) and malformed JSON both degrade to an
  // empty list rather than failing the whole species response.
  private List<CareCardDto> parseCareCards(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          raw,
          objectMapper.getTypeFactory().constructCollectionType(List.class, CareCardDto.class));
    } catch (Exception e) {
      log.warn("Malformed species care cards JSON: {}", e.getMessage());
      return List.of();
    }
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
    // Single batch fetch backs both maps below — avoids a second query just for lastScanAt.
    List<Identification> latestPerPlant = identificationRepository.findLatestPerPlant(plantIds);
    Map<Long, String> healthByPlantId =
        latestPerPlant.stream()
            .collect(Collectors.toMap(Identification::getPlantId, Identification::getHealthStatus));
    // Collectors.toMap throws NPE on a null value, so filter first — createdAt is always set on
    // a real persisted row, but defend against it anyway rather than trust that invariant here.
    Map<Long, Instant> lastScanAtByPlantId =
        latestPerPlant.stream()
            .filter(i -> i.getCreatedAt() != null)
            .collect(Collectors.toMap(Identification::getPlantId, Identification::getCreatedAt));

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
                        healthByPlantId,
                        lastScanAtByPlantId))
            .filter(Objects::nonNull)
            .toList();

    return new PageImpl<>(content, pageable, speciesIdPage.getTotalElements());
  }

  private SpeciesSummaryDto toSummary(
      Species species,
      List<Plant> plants,
      Map<Long, String> healthByPlantId,
      Map<Long, Instant> lastScanAtByPlantId) {
    if (species == null) {
      return null;
    }
    long issueCount =
        plants.stream()
            .map(plant -> healthByPlantId.get(plant.getId()))
            .filter(ISSUES_DETECTED::equals)
            .count();
    String healthSummary = issueCount == 0 ? "All healthy" : issueCount + " issue(s)";
    Instant lastScanAt =
        plants.stream()
            .map(plant -> lastScanAtByPlantId.get(plant.getId()))
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    return SpeciesSummaryDto.builder()
        .speciesId(species.getId())
        .scientificName(species.getScientificName())
        .commonName(species.getCommonName())
        .imageUrl(species.getImageUrl() != null ? species.getImageUrl() : firstPlantPhoto(plants))
        .plantCount(plants.size())
        .healthSummary(healthSummary)
        .lastScanAt(lastScanAt)
        .build();
  }

  // Species.imageUrl only ever comes from AI enrichment and is frequently null (enrichment may
  // not have finished, or failed) — fall back to a real photo from one of the caller's own plants
  // of this species rather than showing a blank thumbnail. plants is already in hand from the
  // batch fetch above, so this is zero extra queries.
  private static String firstPlantPhoto(List<Plant> plants) {
    return plants.stream()
        .map(Plant::getPhotoUrl)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  @Transactional
  @CacheEvict(value = SPECIES_CACHE, key = "#speciesId")
  public SpeciesResponse regenerateDescription(Long speciesId, Long userId) {
    Species species =
        speciesRepository
            .findById(speciesId)
            .orElseThrow(() -> new ResourceNotFoundException("Species", speciesId));
    species.setDescriptionStatus(GenerationStatus.PENDING);
    speciesRepository.save(species);

    Long id = species.getId();
    speciesEnrichmentService.ifPresent(
        service ->
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                  @Override
                  public void afterCommit() {
                    service.enrich(id, AiModelPreference.DEEPSEEK);
                  }
                }));

    return speciesMapper.toResponse(species).toBuilder()
        .careCards(parseCareCards(species.getCareCards()))
        .build();
  }

  private Species patchFactualFields(
      Species species,
      String gbifId,
      String powoId,
      String iucnCategory,
      String family,
      String genus,
      String imageUrl,
      String imageAttribution,
      String imageLicense) {
    boolean changed = false;
    if (gbifId != null && species.getGbifId() == null) {
      species.setGbifId(gbifId);
      changed = true;
    }
    if (powoId != null && species.getPowoId() == null) {
      species.setPowoId(powoId);
      changed = true;
    }
    if (iucnCategory != null && species.getIucnCategory() == null) {
      species.setIucnCategory(iucnCategory);
      changed = true;
    }
    if (family != null && species.getFamily() == null) {
      species.setFamily(family);
      changed = true;
    }
    if (genus != null && species.getGenus() == null) {
      species.setGenus(genus);
      changed = true;
    }
    // Never overwrite a non-null imageUrl — one-time fill only.
    if (imageUrl != null && species.getImageUrl() == null) {
      species.setImageUrl(imageUrl);
      species.setImageAttribution(imageAttribution);
      species.setImageLicense(imageLicense);
      if (species.getIdentitySource() == null) {
        species.setIdentitySource("PLANTNET");
      }
      changed = true;
    }
    if (changed) {
      species = speciesRepository.save(species);
      log.info(
          "Patched botanical fields on existing species: id={}, scientificName={}",
          species.getId(),
          species.getScientificName());
    }
    return species;
  }

  private Species createSpecies(
      String scientificName,
      String commonName,
      AiModelPreference preference,
      String gbifId,
      String powoId,
      String iucnCategory,
      String family,
      String genus,
      String imageUrl,
      String imageAttribution,
      String imageLicense) {
    Species species =
        speciesRepository.save(
            Species.builder()
                .scientificName(scientificName)
                .commonName(commonName)
                .gbifId(gbifId)
                .powoId(powoId)
                .iucnCategory(iucnCategory)
                .family(family)
                .genus(genus)
                .imageUrl(imageUrl)
                .imageAttribution(imageAttribution)
                .imageLicense(imageLicense)
                .identitySource(imageUrl != null ? "PLANTNET" : null)
                .status(SpeciesStatus.ACTIVE)
                .build());
    log.info("Species created: id={}, scientificName={}", species.getId(), scientificName);

    // Register enrichment to fire AFTER the outer transaction commits.
    // Firing it inline (before commit) means enrich()'s own @Transactional opens a new
    // connection and calls findById — but the species row hasn't reached the DB yet, so
    // findById returns empty and enrichment is silently skipped (descriptionStatus stays PENDING
    // forever). afterCommit() guarantees the row is visible before the async thread starts.
    Long speciesId = species.getId();
    speciesEnrichmentService.ifPresent(
        service ->
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                  @Override
                  public void afterCommit() {
                    service.enrich(speciesId, preference);
                  }
                }));

    return species;
  }
}
