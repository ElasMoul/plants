package com.plantpal.identification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.CareCardDto;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.mapper.IdentificationMapper;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.identification.service.IdentificationService;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.shared.storage.FileStorageService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdentificationServiceImpl implements IdentificationService {

  private static final Logger log = LoggerFactory.getLogger(IdentificationServiceImpl.class);

  private static final int MAX_IMAGES = 5;
  private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
  private static final int DEEPSEEK_RATE_LIMIT = 20;
  private static final List<String> ALLOWED_TYPES =
      List.of("image/jpeg", "image/png", "image/webp");

  private final PlantNetClient plantNetClient;
  private final DeepSeekClient deepSeekClient;
  private final IdentificationRepository identificationRepository;
  private final IdentificationMapper identificationMapper;
  private final PlantRepository plantRepository;
  private final ReminderRepository reminderRepository;
  private final FileStorageService fileStorageService;
  private final ObjectMapper objectMapper;

  private final Map<Long, Bucket> deepSeekBuckets = new ConcurrentHashMap<>();

  public IdentificationServiceImpl(
      PlantNetClient plantNetClient,
      DeepSeekClient deepSeekClient,
      IdentificationRepository identificationRepository,
      IdentificationMapper identificationMapper,
      PlantRepository plantRepository,
      ReminderRepository reminderRepository,
      FileStorageService fileStorageService,
      ObjectMapper objectMapper) {
    this.plantNetClient = plantNetClient;
    this.deepSeekClient = deepSeekClient;
    this.identificationRepository = identificationRepository;
    this.identificationMapper = identificationMapper;
    this.plantRepository = plantRepository;
    this.reminderRepository = reminderRepository;
    this.fileStorageService = fileStorageService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Async("aiTaskExecutor")
  public CompletableFuture<IdentificationResponse> identify(
      List<MultipartFile> images, List<String> organs, Long plantId, Long userId) {

    validateImages(images);

    Identification identification = null;
    try {
      // Step 1: Save photos, collect URLs
      List<String> photoUrls = new ArrayList<>();
      for (MultipartFile image : images) {
        photoUrls.add(fileStorageService.savePhoto(image));
      }

      // Step 2: Persist with PENDING status
      identification =
          Identification.builder()
              .userId(userId)
              .plantId(plantId)
              .photoUrl(photoUrls.get(0))
              .status(IdentificationStatus.PENDING)
              .build();
      identification = identificationRepository.save(identification);
      log.info("Identification created: id={}, userId={}", identification.getId(), userId);

      // Step 3: Call PlantNet (sequential — species needed for DeepSeek)
      PlantNetResponse plantNetResponse = plantNetClient.identify(images, organs);
      PlantNetResult topResult = plantNetResponse.results().get(0);
      String scientificName = topResult.species().scientificNameWithoutAuthor();
      List<String> commonNames = topResult.species().commonNames();
      String commonName =
          (commonNames != null && !commonNames.isEmpty()) ? commonNames.get(0) : null;

      // Step 4: Fire DeepSeek in parallel (ready for T2.9 to add a third parallel future)
      CompletableFuture<CarePlanDto> carePlanFuture =
          CompletableFuture.supplyAsync(
              () -> generateCarePlanSafely(scientificName, commonName, userId));

      // Step 5: Await care plan
      CarePlanDto carePlan = carePlanFuture.join();

      // Step 6: Persist completed identification with care plan
      identification.setScientificName(scientificName);
      identification.setCommonName(commonName);
      identification.setConfidence(topResult.score());
      identification.setRawResponse(serializeToJson(plantNetResponse));
      identification.setCarePlan(serializeToJson(carePlan));
      identification.setStatus(IdentificationStatus.COMPLETED);
      identification = identificationRepository.save(identification);

      // Step 7: Update linked plant and auto-create reminders
      if (plantId != null && plantRepository.existsByIdAndUserId(plantId, userId)) {
        updatePlantSpecies(plantId, userId, scientificName, commonName);
        createRemindersFromCarePlan(carePlan, plantId, userId);
      }

      // Step 8: Build response
      List<PlantNetResult> topResults = plantNetResponse.results().stream().limit(3).toList();
      IdentificationResponse response = buildResponse(identification, topResults, carePlan);
      return CompletableFuture.completedFuture(response);

    } catch (PlantPalException e) {
      markFailed(identification);
      throw e;
    } catch (Exception e) {
      log.error("Identification failed for userId={}", userId, e);
      markFailed(identification);
      throw new PlantPalException("Identification failed: " + e.getMessage(), 500);
    }
  }

  @Override
  public Page<IdentificationResponse> getPlantIdentifications(
      Long plantId, Long userId, Pageable pageable) {
    if (!plantRepository.existsByIdAndUserId(plantId, userId)) {
      throw new ResourceNotFoundException("Plant not found");
    }
    return identificationRepository
        .findByPlantIdOrderByCreatedAtDesc(plantId, pageable)
        .map(
            entity -> {
              IdentificationResponse resp = identificationMapper.toResponse(entity);
              resp.setCarePlan(parseCarePlan(entity.getCarePlan()));
              return resp;
            });
  }

  private CarePlanDto generateCarePlanSafely(String species, String commonName, Long userId) {
    if (!consumeRateLimit(userId)) {
      log.warn("DeepSeek rate limit exceeded for userId={}", userId);
      return fallbackCarePlan();
    }
    try {
      String raw = deepSeekClient.generateCarePlan(species, commonName, null);
      return parseCarePlan(raw);
    } catch (Exception e) {
      log.warn("DeepSeek care plan failed for species={}: {}", species, e.getMessage());
      return fallbackCarePlan();
    }
  }

  private CarePlanDto parseCarePlan(String raw) {
    if (raw == null || raw.isBlank()) {
      return fallbackCarePlan();
    }
    try {
      CarePlanDto plan = objectMapper.readValue(raw, CarePlanDto.class);
      if (plan.getCareCards() == null || plan.getCareCards().isEmpty()) {
        return fallbackCarePlan();
      }
      return plan;
    } catch (JsonProcessingException e) {
      log.warn("Malformed care plan JSON, using fallback: {}", e.getMessage());
      return fallbackCarePlan();
    }
  }

  private CarePlanDto fallbackCarePlan() {
    CareCardDto wateringCard =
        CareCardDto.builder()
            .type("WATERING")
            .title("Watering")
            .icon("water_drop")
            .summary("Water when the top 2cm of soil feels dry")
            .detail(
                "Check the soil moisture before watering. Stick your finger about 2cm into the"
                    + " soil — if it feels dry, it's time to water. If it's still moist, wait"
                    + " another day or two. Overwatering is the most common cause of plant death.")
            .urgency("MEDIUM")
            .seasonalVariation("Water less frequently in winter when growth slows.")
            .build();
    return CarePlanDto.builder()
        .wateringFrequencyDays(7)
        .fertilizingFrequencyDays(0)
        .repottingFrequencyMonths(12)
        .careCards(List.of(wateringCard))
        .beginnerWarnings(List.of())
        .build();
  }

  private void createRemindersFromCarePlan(CarePlanDto carePlan, Long plantId, Long userId) {
    Instant now = Instant.now();

    reminderRepository.save(
        Reminder.builder()
            .plantId(plantId)
            .userId(userId)
            .careType(CareType.WATERING)
            .frequencyDays(carePlan.getWateringFrequencyDays())
            .nextDueAt(now.plus(carePlan.getWateringFrequencyDays(), ChronoUnit.DAYS))
            .enabled(true)
            .build());

    if (carePlan.getFertilizingFrequencyDays() > 0) {
      reminderRepository.save(
          Reminder.builder()
              .plantId(plantId)
              .userId(userId)
              .careType(CareType.FERTILIZING)
              .frequencyDays(carePlan.getFertilizingFrequencyDays())
              .nextDueAt(now.plus(carePlan.getFertilizingFrequencyDays(), ChronoUnit.DAYS))
              .enabled(true)
              .build());
    }

    int repottingDays = carePlan.getRepottingFrequencyMonths() * 30;
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

  private boolean consumeRateLimit(Long userId) {
    Bucket bucket =
        deepSeekBuckets.computeIfAbsent(
            userId,
            id ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(DEEPSEEK_RATE_LIMIT)
                            .refillIntervally(DEEPSEEK_RATE_LIMIT, Duration.ofHours(1))
                            .build())
                    .build());
    return bucket.tryConsume(1);
  }

  private void updatePlantSpecies(
      Long plantId, Long userId, String scientificName, String commonName) {
    plantRepository
        .findByIdAndUserId(plantId, userId)
        .ifPresent(
            plant -> {
              plant.setSpecies(scientificName);
              plant.setCommonName(commonName);
              plantRepository.save(plant);
              log.info("Updated plant species: plantId={}, species={}", plantId, scientificName);
            });
  }

  private void validateImages(List<MultipartFile> images) {
    if (images == null || images.isEmpty() || images.size() > MAX_IMAGES) {
      throw new ValidationException("Between 1 and " + MAX_IMAGES + " images are required");
    }
    for (MultipartFile image : images) {
      if (image.getSize() > MAX_IMAGE_BYTES) {
        throw new ValidationException("Each image must be 10 MB or smaller");
      }
      String contentType = image.getContentType();
      if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
        throw new ValidationException("Images must be JPEG, PNG, or WebP");
      }
    }
  }

  private void markFailed(Identification identification) {
    if (identification == null) return;
    try {
      identification.setStatus(IdentificationStatus.FAILED);
      identificationRepository.save(identification);
    } catch (Exception ex) {
      log.error("Failed to mark identification as FAILED: id={}", identification.getId(), ex);
    }
  }

  private String serializeToJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize object to JSON: {}", e.getMessage());
      return null;
    }
  }

  private IdentificationResponse buildResponse(
      Identification entity, List<PlantNetResult> topResults, CarePlanDto carePlan) {
    return IdentificationResponse.builder()
        .id(entity.getId())
        .plantId(entity.getPlantId())
        .scientificName(entity.getScientificName())
        .commonName(entity.getCommonName())
        .confidence(entity.getConfidence())
        .status(entity.getStatus())
        .photoUrl(entity.getPhotoUrl())
        .createdAt(entity.getCreatedAt())
        .topResults(topResults)
        .carePlan(carePlan)
        .build();
  }
}
