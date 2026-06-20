package com.plantpal.plant.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.SaveIdentificationAsPlantRequest;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapper;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.PlantService;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.shared.dto.RestPage;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

@Service
public class PlantServiceImpl implements PlantService {

  private static final Logger log = LoggerFactory.getLogger(PlantServiceImpl.class);

  private static final String PLANTS_CACHE = "plants";
  private static final String NOT_FOUND_MSG = "Plant not found or not owned by user";

  private final PlantRepository plantRepository;
  private final PlantMapper plantMapper;
  private final IdentificationRepository identificationRepository;
  private final ReminderRepository reminderRepository;
  private final ObjectMapper objectMapper;

  public PlantServiceImpl(
      PlantRepository plantRepository,
      PlantMapper plantMapper,
      IdentificationRepository identificationRepository,
      ReminderRepository reminderRepository,
      ObjectMapper objectMapper) {
    this.plantRepository = plantRepository;
    this.plantMapper = plantMapper;
    this.identificationRepository = identificationRepository;
    this.reminderRepository = reminderRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  @CacheEvict(value = PLANTS_CACHE, allEntries = true)
  public PlantResponse createPlant(CreatePlantRequest request, Long userId) {
    Plant plant = plantMapper.toEntity(request);
    plant.setUserId(userId);
    plant.setStatus(PlantStatus.ACTIVE);

    plant = plantRepository.save(plant);
    log.info("Plant created: id={}, userId={}", plant.getId(), userId);

    return plantMapper.toResponse(plant);
  }

  @Override
  @Transactional
  @CacheEvict(value = PLANTS_CACHE, allEntries = true)
  public PlantResponse updatePlant(Long id, UpdatePlantRequest request, Long userId) {
    Plant plant = findOwnedPlant(id, userId);

    applyUpdates(plant, request);
    plant = plantRepository.save(plant);
    log.info("Plant updated: id={}, userId={}", id, userId);

    return plantMapper.toResponse(plant);
  }

  @Override
  @Transactional
  @CacheEvict(value = PLANTS_CACHE, allEntries = true)
  public void archivePlant(Long id, Long userId) {
    Plant plant = findOwnedPlant(id, userId);
    plant.setStatus(PlantStatus.ARCHIVED);
    plantRepository.save(plant);
    disableRemindersForPlant(id);
    log.info("Plant archived: id={}, userId={}", id, userId);
  }

  private void disableRemindersForPlant(Long plantId) {
    List<Reminder> reminders = reminderRepository.findByPlantIdAndEnabledTrue(plantId);
    reminders.forEach(r -> r.setEnabled(false));
    reminderRepository.saveAll(reminders);
    log.info("Disabled {} reminder(s) for archived plant: plantId={}", reminders.size(), plantId);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(
      value = PLANTS_CACHE,
      key = "'u:' + #userId + ':p:' + #pageable.pageNumber + ':s:' + #pageable.pageSize")
  public Page<PlantResponse> getUserPlants(Long userId, Pageable pageable) {
    log.info("Fetching plants for userId={}, page={}", userId, pageable.getPageNumber());
    Page<Plant> plants =
        plantRepository.findAllByUserIdAndStatus(userId, PlantStatus.ACTIVE, pageable);
    return buildEnrichedPage(plants, pageable);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(
      value = PLANTS_CACHE,
      key =
          "'u:' + #userId + ':sp:' + #speciesId + ':p:' + #pageable.pageNumber + ':s:'"
              + " + #pageable.pageSize")
  public Page<PlantResponse> getUserPlants(Long userId, Long speciesId, Pageable pageable) {
    log.info(
        "Fetching plants for userId={}, speciesId={}, page={}",
        userId,
        speciesId,
        pageable.getPageNumber());
    Page<Plant> plants =
        plantRepository.findAllByUserIdAndSpeciesIdAndStatus(
            userId, speciesId, PlantStatus.ACTIVE, pageable);
    return buildEnrichedPage(plants, pageable);
  }

  private Page<PlantResponse> buildEnrichedPage(Page<Plant> plants, Pageable pageable) {
    Page<PlantResponse> page = plants.map(plantMapper::toResponse);
    List<Long> plantIds = page.getContent().stream().map(PlantResponse::getId).toList();
    List<PlantResponse> enriched = enrichWithHealthAndWater(page.getContent(), plantIds);
    return new RestPage<>(new PageImpl<>(enriched, pageable, page.getTotalElements()));
  }

  @Override
  @Transactional(readOnly = true)
  public PlantResponse getPlant(Long id, Long userId) {
    Plant plant = findOwnedPlant(id, userId);
    log.info("Fetched plant: id={}, userId={}", id, userId);
    PlantResponse response = plantMapper.toResponse(plant);
    return enrichWithHealthAndWater(List.of(response), List.of(id)).get(0);
  }

  private List<PlantResponse> enrichWithHealthAndWater(
      List<PlantResponse> responses, List<Long> plantIds) {
    if (plantIds.isEmpty()) {
      return responses;
    }

    List<Identification> latestPerPlant = identificationRepository.findLatestPerPlant(plantIds);

    Map<Long, String> healthByPlantId =
        latestPerPlant.stream()
            .collect(Collectors.toMap(Identification::getPlantId, Identification::getHealthStatus));

    Map<Long, Instant> lastScanAtByPlantId =
        latestPerPlant.stream()
            .collect(Collectors.toMap(Identification::getPlantId, Identification::getCreatedAt));

    Map<Long, Integer> nextWaterDaysByPlantId =
        reminderRepository.findNearestWateringPerPlant(plantIds).stream()
            .collect(
                Collectors.toMap(
                    Reminder::getPlantId,
                    r -> (int) ChronoUnit.DAYS.between(Instant.now(), r.getNextDueAt())));

    return responses.stream()
        .map(
            response ->
                response.toBuilder()
                    .healthStatus(healthByPlantId.get(response.getId()))
                    .nextWaterDays(nextWaterDaysByPlantId.get(response.getId()))
                    .lastScanAt(lastScanAtByPlantId.get(response.getId()))
                    .build())
        .toList();
  }

  @Override
  @Transactional
  @CacheEvict(value = PLANTS_CACHE, allEntries = true)
  public PlantResponse saveFromIdentification(
      SaveIdentificationAsPlantRequest request, Long userId) {
    Identification identification =
        identificationRepository
            .findById(request.getIdentificationId())
            .filter(i -> userId.equals(i.getUserId()))
            .orElseThrow(() -> new ResourceNotFoundException("Identification not found"));

    String nickname = resolveNickname(request.getNickname(), identification);

    Plant plant =
        Plant.builder()
            .userId(userId)
            .nickname(nickname)
            .species(identification.getScientificName())
            .commonName(identification.getCommonName())
            .photoUrl(identification.getPhotoUrl())
            .location(request.getLocation())
            .status(PlantStatus.ACTIVE)
            .build();
    plant = plantRepository.save(plant);
    log.info(
        "Plant saved from identification: plantId={}, identificationId={}, userId={}",
        plant.getId(),
        request.getIdentificationId(),
        userId);

    identification.setPlantId(plant.getId());
    identificationRepository.save(identification);

    createRemindersIfPossible(identification.getCarePlan(), plant.getId(), userId);

    return plantMapper.toResponse(plant);
  }

  private String resolveNickname(String requested, Identification identification) {
    if (requested != null && !requested.isBlank()) return requested;
    if (identification.getCommonName() != null) return identification.getCommonName();
    if (identification.getScientificName() != null) return identification.getScientificName();
    return "My Plant";
  }

  private void createRemindersIfPossible(String carePlanJson, Long plantId, Long userId) {
    if (carePlanJson == null || carePlanJson.isBlank()) return;
    CarePlanDto plan;
    try {
      plan = objectMapper.readValue(carePlanJson, CarePlanDto.class);
    } catch (JsonProcessingException e) {
      log.warn("Could not parse care plan for reminders: plantId={}", plantId);
      return;
    }
    Instant now = Instant.now();
    reminderRepository.save(
        Reminder.builder()
            .plantId(plantId)
            .userId(userId)
            .careType(CareType.WATERING)
            .frequencyDays(plan.getWateringFrequencyDays())
            .nextDueAt(now.plus(plan.getWateringFrequencyDays(), ChronoUnit.DAYS))
            .enabled(true)
            .build());
    if (plan.getFertilizingFrequencyDays() > 0) {
      reminderRepository.save(
          Reminder.builder()
              .plantId(plantId)
              .userId(userId)
              .careType(CareType.FERTILIZING)
              .frequencyDays(plan.getFertilizingFrequencyDays())
              .nextDueAt(now.plus(plan.getFertilizingFrequencyDays(), ChronoUnit.DAYS))
              .enabled(true)
              .build());
    }
    int repottingDays = plan.getRepottingFrequencyMonths() * 30;
    reminderRepository.save(
        Reminder.builder()
            .plantId(plantId)
            .userId(userId)
            .careType(CareType.REPOTTING)
            .frequencyDays(repottingDays)
            .nextDueAt(now.plus(repottingDays, ChronoUnit.DAYS))
            .enabled(true)
            .build());
    log.info("Auto-created reminders from care plan: plantId={}, userId={}", plantId, userId);
  }

  private Plant findOwnedPlant(Long id, Long userId) {
    return plantRepository
        .findByIdAndUserIdAndStatus(id, userId, PlantStatus.ACTIVE)
        .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MSG));
  }

  private void applyUpdates(Plant plant, UpdatePlantRequest request) {
    if (request.getNickname() != null) {
      plant.setNickname(request.getNickname());
    }
    if (request.getSpecies() != null) {
      plant.setSpecies(request.getSpecies());
    }
    if (request.getCommonName() != null) {
      plant.setCommonName(request.getCommonName());
    }
    if (request.getPhotoUrl() != null) {
      plant.setPhotoUrl(request.getPhotoUrl());
    }
    if (request.getLocation() != null) {
      plant.setLocation(request.getLocation());
    }
    if (request.getNotes() != null) {
      plant.setNotes(request.getNotes());
    }
    if (request.getAcquiredAt() != null) {
      plant.setAcquiredAt(request.getAcquiredAt());
    }
  }
}
