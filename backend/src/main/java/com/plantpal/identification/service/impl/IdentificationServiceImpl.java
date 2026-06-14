package com.plantpal.identification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.VisionAnnotationClient;
import com.plantpal.identification.dto.AnnotationRegionDto;
import com.plantpal.identification.dto.CareCardDto;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.dto.DeepSeekPlantResult;
import com.plantpal.identification.dto.IdentificationResponse;
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

  private final DeepSeekClient deepSeekClient;
  private final VisionAnnotationClient visionAnnotationClient;
  private final IdentificationRepository identificationRepository;
  private final IdentificationMapper identificationMapper;
  private final PlantRepository plantRepository;
  private final ReminderRepository reminderRepository;
  private final FileStorageService fileStorageService;
  private final ObjectMapper objectMapper;

  private final Map<Long, Bucket> deepSeekBuckets = new ConcurrentHashMap<>();

  public IdentificationServiceImpl(
      DeepSeekClient deepSeekClient,
      VisionAnnotationClient visionAnnotationClient,
      IdentificationRepository identificationRepository,
      IdentificationMapper identificationMapper,
      PlantRepository plantRepository,
      ReminderRepository reminderRepository,
      FileStorageService fileStorageService,
      ObjectMapper objectMapper) {
    this.deepSeekClient = deepSeekClient;
    this.visionAnnotationClient = visionAnnotationClient;
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

      // Step 3: Rate-limit check before hitting DeepSeek
      if (!consumeRateLimit(userId)) {
        throw new PlantPalException("AI identification rate limit reached — try again later", 429);
      }

      // Step 4: Fire identification + annotation in parallel
      MultipartFile primaryImage = images.get(0);
      byte[] imageBytes = primaryImage.getBytes();
      String mediaType = primaryImage.getContentType();

      CompletableFuture<String> identificationFuture =
          CompletableFuture.supplyAsync(() -> deepSeekClient.identifyPlant(imageBytes, mediaType));
      CompletableFuture<String> annotationFuture =
          CompletableFuture.supplyAsync(
              () -> visionAnnotationClient.analyzeRegions(imageBytes, mediaType));

      String rawResult;
      try {
        rawResult = identificationFuture.join();
      } catch (java.util.concurrent.CompletionException ce) {
        Throwable cause = ce.getCause();
        throw (cause instanceof PlantPalException pex)
            ? pex
            : new PlantPalException("Identification failed: " + cause.getMessage(), 500);
      }
      String annotationJson = annotationFuture.join();

      // Step 5: Parse combined result; fall back gracefully if DeepSeek JSON is malformed
      DeepSeekPlantResult result = parseIdentificationResult(rawResult);
      CarePlanDto carePlan =
          result.getCarePlan() != null ? result.getCarePlan() : fallbackCarePlan();

      // Step 6: Persist completed identification
      identification.setScientificName(result.getSpecies());
      identification.setCommonName(result.getCommonName());
      identification.setConfidence(confidenceToScore(result.getConfidence()));
      identification.setHealthStatus(result.getHealthStatus());
      identification.setHealthNotes(result.getHealthNotes());
      identification.setRawResponse(rawResult);
      identification.setCarePlan(serializeToJson(carePlan));
      identification.setAnnotationRegions(annotationJson);
      identification.setStatus(IdentificationStatus.COMPLETED);
      identification = identificationRepository.save(identification);

      // Step 7: Update linked plant and auto-create reminders
      if (plantId != null && plantRepository.existsByIdAndUserId(plantId, userId)) {
        updatePlantSpecies(plantId, userId, result.getSpecies(), result.getCommonName());
        createRemindersFromCarePlan(carePlan, plantId, userId);
      }

      // Step 8: Build response
      IdentificationResponse response =
          buildResponse(identification, carePlan, parseAnnotationRegions(annotationJson));
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
              resp.setAnnotationRegions(parseAnnotationRegions(entity.getAnnotationRegions()));
              return resp;
            });
  }

  private DeepSeekPlantResult parseIdentificationResult(String raw) {
    try {
      DeepSeekPlantResult result = objectMapper.readValue(raw, DeepSeekPlantResult.class);
      if (result.getCommonName() == null) result.setCommonName("Unknown Plant");
      return result;
    } catch (JsonProcessingException e) {
      log.warn("Malformed identification JSON from DeepSeek, using fallback: {}", e.getMessage());
      return new DeepSeekPlantResult(null, "Unknown Plant", "LOW", "UNKNOWN", null, null);
    }
  }

  private double confidenceToScore(String confidence) {
    if (confidence == null) return 0.3;
    return switch (confidence.toUpperCase()) {
      case "HIGH" -> 0.9;
      case "MEDIUM" -> 0.6;
      default -> 0.3;
    };
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

  private List<AnnotationRegionDto> parseAnnotationRegions(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      var root = objectMapper.readTree(json);
      var regions = root.get("regions");
      if (regions == null || !regions.isArray()) return List.of();
      return objectMapper.convertValue(
          regions,
          objectMapper
              .getTypeFactory()
              .constructCollectionType(List.class, AnnotationRegionDto.class));
    } catch (Exception e) {
      log.warn("Malformed annotation regions JSON: {}", e.getMessage());
      return List.of();
    }
  }

  private IdentificationResponse buildResponse(
      Identification entity, CarePlanDto carePlan, List<AnnotationRegionDto> annotationRegions) {
    return IdentificationResponse.builder()
        .id(entity.getId())
        .plantId(entity.getPlantId())
        .scientificName(entity.getScientificName())
        .commonName(entity.getCommonName())
        .confidence(entity.getConfidence())
        .healthStatus(entity.getHealthStatus())
        .healthNotes(entity.getHealthNotes())
        .status(entity.getStatus())
        .photoUrl(entity.getPhotoUrl())
        .createdAt(entity.getCreatedAt())
        .topResults(List.of())
        .carePlan(carePlan)
        .annotationRegions(annotationRegions)
        .build();
  }
}
