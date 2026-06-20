package com.plantpal.identification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.client.VisionAnnotationClient;
import com.plantpal.identification.config.KafkaTopicConfig;
import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.dto.AddCareCardRequest;
import com.plantpal.identification.dto.AnnotationRegionDto;
import com.plantpal.identification.dto.CareCardDto;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.dto.CureAdviceRequest;
import com.plantpal.identification.dto.CureAdviceResponse;
import com.plantpal.identification.dto.DeepSeekPlantResult;
import com.plantpal.identification.dto.IdentificationPendingResponse;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.dto.PlantMatchDto;
import com.plantpal.identification.dto.PlantSummaryDto;
import com.plantpal.identification.dto.ResolvePlantRequest;
import com.plantpal.identification.dto.ResolveSpeciesRequest;
import com.plantpal.identification.dto.SpeciesMatchDto;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.event.IdentificationCompletedEvent;
import com.plantpal.identification.event.IdentificationRequestedEvent;
import com.plantpal.identification.mapper.IdentificationMapper;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.identification.service.IdentificationService;
import com.plantpal.identification.util.ActionPlanValidator;
import com.plantpal.plant.dto.SaveIdentificationAsPlantRequest;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.PlantService;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.shared.storage.FileStorageService;
import com.plantpal.shared.util.ImageUtil;
import com.plantpal.species.entity.Species;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesService;
import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.repository.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdentificationServiceImpl implements IdentificationService {

  private static final Logger log = LoggerFactory.getLogger(IdentificationServiceImpl.class);

  private static final int MAX_IMAGES = 5;
  private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
  private static final int DEEPSEEK_RATE_LIMIT = 20;
  private static final int CURE_ADVICE_RATE_LIMIT = 10;
  private static final int SOURCE_IMAGE_MAX_SIDE_PX = 1024;
  private static final String PLANTS_CACHE = "plants";
  private static final List<String> ALLOWED_TYPES =
      List.of("image/jpeg", "image/png", "image/webp");

  private final DeepSeekClient deepSeekClient;
  private final GitHubModelsClient gitHubModelsClient;
  private final VisionAnnotationClient visionAnnotationClient;
  private final IdentificationRepository identificationRepository;
  private final IdentificationMapper identificationMapper;
  private final PlantRepository plantRepository;
  private final ReminderRepository reminderRepository;
  private final FileStorageService fileStorageService;
  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;
  private final PlantNetClient plantNetClient;
  private final OllamaClient ollamaClient;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final CacheManager cacheManager;
  private final SpeciesRepository speciesRepository;
  private final SpeciesService speciesService;
  private final PlantService plantService;

  private final Map<Long, Bucket> deepSeekBuckets = new ConcurrentHashMap<>();
  private final Map<Long, Bucket> cureAdviceBuckets = new ConcurrentHashMap<>();

  public IdentificationServiceImpl(
      DeepSeekClient deepSeekClient,
      VisionAnnotationClient visionAnnotationClient,
      IdentificationRepository identificationRepository,
      IdentificationMapper identificationMapper,
      PlantRepository plantRepository,
      ReminderRepository reminderRepository,
      FileStorageService fileStorageService,
      ObjectMapper objectMapper,
      GitHubModelsClient gitHubModelsClient,
      UserRepository userRepository,
      PlantNetClient plantNetClient,
      OllamaClient ollamaClient,
      KafkaTemplate<String, Object> kafkaTemplate,
      CacheManager cacheManager,
      SpeciesRepository speciesRepository,
      SpeciesService speciesService,
      PlantService plantService) {
    this.deepSeekClient = deepSeekClient;
    this.gitHubModelsClient = gitHubModelsClient;
    this.visionAnnotationClient = visionAnnotationClient;
    this.identificationRepository = identificationRepository;
    this.identificationMapper = identificationMapper;
    this.plantRepository = plantRepository;
    this.reminderRepository = reminderRepository;
    this.fileStorageService = fileStorageService;
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
    this.plantNetClient = plantNetClient;
    this.ollamaClient = ollamaClient;
    this.kafkaTemplate = kafkaTemplate;
    this.cacheManager = cacheManager;
    this.speciesRepository = speciesRepository;
    this.speciesService = speciesService;
    this.plantService = plantService;
  }

  @Override
  public CompletableFuture<IdentificationPendingResponse> submitIdentification(
      List<MultipartFile> images, Long plantId, Long speciesId, Long userId, List<String> organs) {

    validateImages(images);

    // Step 1: Save photos, collect URLs
    List<String> photoUrls = new ArrayList<>();
    for (MultipartFile image : images) {
      photoUrls.add(fileStorageService.savePhoto(image));
    }

    // Step 2: Persist with PENDING status (no AI call yet)
    // speciesId is only ever passed by Flow 2 (scan from a Species page) — Flow 1 (Garden FAB)
    // leaves it null and resolves species after the AI result comes back (see resolveSpecies()).
    Identification identification =
        Identification.builder()
            .userId(userId)
            .plantId(plantId)
            .speciesId(speciesId)
            .photoUrl(photoUrls.get(0))
            .status(IdentificationStatus.PENDING)
            .build();
    identification = identificationRepository.save(identification);
    log.info("Identification submitted: id={}, userId={}", identification.getId(), userId);

    // Step 3: Rate-limit check before publishing — fail fast, no wasted Kafka message
    if (!consumeRateLimit(userId)) {
      throw new PlantPalException("AI identification rate limit reached — try again later", 429);
    }

    // Step 4: Publish event for async processing by the Kafka consumer
    AiModelPreference preference = loadUserPreference(userId);
    IdentificationRequestedEvent event =
        IdentificationRequestedEvent.builder()
            .identificationId(identification.getId())
            .userId(userId)
            .photoUrl(identification.getPhotoUrl())
            .aiModelPreference(preference.name())
            .organs(organs)
            .requestedAt(Instant.now())
            .build();
    kafkaTemplate.send(KafkaTopicConfig.IDENTIFICATION_REQUESTED_TOPIC, event);
    log.info("Published IdentificationRequestedEvent: id={}", identification.getId());

    return CompletableFuture.completedFuture(
        IdentificationPendingResponse.builder()
            .identificationId(identification.getId())
            .status(identification.getStatus().name())
            .build());
  }

  @Override
  @Async("aiTaskExecutor")
  public void processIdentification(IdentificationRequestedEvent event) {
    Identification identification =
        identificationRepository.findById(event.getIdentificationId()).orElse(null);
    if (identification == null) {
      log.error("Identification not found for event: id={}", event.getIdentificationId());
      return;
    }

    try {
      byte[] rawBytes = fileStorageService.loadPhotoBytes(identification.getPhotoUrl());
      String mediaType = resolveMediaType(identification.getPhotoUrl());
      AiModelPreference preference = AiModelPreference.valueOf(event.getAiModelPreference());
      Long plantId = identification.getPlantId();
      Long userId = identification.getUserId();

      // Normalize once so the stored dimensions match what every AI provider actually sees.
      byte[] imageBytes = ImageUtil.resizeAndConvertToJpeg(rawBytes, SOURCE_IMAGE_MAX_SIDE_PX);
      int[] dims = ImageUtil.readDimensions(imageBytes);
      identification.setSourceImageWidth(dims[0]);
      identification.setSourceImageHeight(dims[1]);

      // Fire identification + annotation in parallel
      CompletableFuture<IdentificationOutcome> identificationFuture =
          CompletableFuture.supplyAsync(
              () -> runIdentification(preference, imageBytes, mediaType, event.getOrgans()));
      CompletableFuture<String> annotationFuture =
          CompletableFuture.supplyAsync(
              () -> visionAnnotationClient.analyzeRegions(imageBytes, mediaType));

      IdentificationOutcome outcome;
      try {
        outcome = identificationFuture.join();
      } catch (CompletionException ce) {
        Throwable cause = ce.getCause();
        throw (cause instanceof PlantPalException pex)
            ? pex
            : new PlantPalException("Identification failed: " + cause.getMessage(), 500);
      }
      String rawResult = outcome.rawJson();
      String annotationJson = annotationFuture.join();

      // Parse combined result; fall back gracefully if AI JSON is malformed
      DeepSeekPlantResult result = parseIdentificationResult(rawResult);
      CarePlanDto carePlan =
          result.getCarePlan() != null ? result.getCarePlan() : fallbackCarePlan();
      normalizeActionPlans(carePlan);

      // Persist completed identification
      identification.setScientificName(result.getSpecies());
      identification.setCommonName(result.getCommonName());
      identification.setConfidence(confidenceToScore(result.getConfidence()));
      identification.setHealthStatus(result.getHealthStatus());
      identification.setHealthNotes(result.getHealthNotes());
      identification.setRawResponse(rawResult);
      identification.setCarePlan(serializeToJson(carePlan));
      identification.setAnnotationRegions(annotationJson);
      identification.setAiModelUsed(outcome.providerUsed());
      identification.setStatus(IdentificationStatus.COMPLETED);
      identification = identificationRepository.save(identification);
      evictPlantsCache();

      // Update linked plant and auto-create reminders
      if (plantId != null && plantRepository.existsByIdAndUserId(plantId, userId)) {
        updatePlantSpecies(
            plantId, userId, result.getSpecies(), result.getCommonName(), identification.getId());
        createRemindersFromCarePlan(carePlan, plantId, userId);
      }

      publishCompletedEvent(identification.getId(), IdentificationStatus.COMPLETED);
      log.info("Identification processed: id={}", identification.getId());

    } catch (Exception e) {
      log.error("Identification processing failed: id={}", identification.getId(), e);
      markFailed(identification);
      publishCompletedEvent(identification.getId(), IdentificationStatus.FAILED);
    }
  }

  @Override
  public IdentificationResponse getIdentification(Long id, Long userId) {
    Identification entity =
        identificationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Identification not found"));
    if (!entity.getUserId().equals(userId)) {
      throw new ResourceNotFoundException("Identification not found");
    }
    IdentificationResponse response = identificationMapper.toResponse(entity);
    response.setCarePlan(parseCarePlan(entity.getCarePlan()));
    response.setAnnotationRegions(parseAnnotationRegions(entity.getAnnotationRegions()));
    return response;
  }

  @Override
  public Page<IdentificationResponse> getUserIdentifications(Long userId, Pageable pageable) {
    return identificationRepository
        .findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(
            entity -> {
              IdentificationResponse resp = identificationMapper.toResponse(entity);
              resp.setCarePlan(parseCarePlan(entity.getCarePlan()));
              resp.setAnnotationRegions(parseAnnotationRegions(entity.getAnnotationRegions()));
              return resp;
            });
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

  @Override
  @Async("aiTaskExecutor")
  public CompletableFuture<CureAdviceResponse> getCureAdvice(
      Long id, CureAdviceRequest req, Long userId) {
    Identification identification =
        identificationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Identification not found"));
    if (!identification.getUserId().equals(userId)) {
      throw new ResourceNotFoundException("Identification not found");
    }
    if (!consumeCureRateLimit(userId)) {
      throw new PlantPalException("Cure advice rate limit reached — try again later", 429);
    }
    String raw = deepSeekClient.generateCureAdvice(req.getSpecies(), req.getRegionLabel());
    return CompletableFuture.completedFuture(parseCureAdvice(raw));
  }

  @Override
  public CarePlanDto addCareCard(Long id, AddCareCardRequest req, Long userId) {
    Identification identification =
        identificationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Identification not found"));
    if (!identification.getUserId().equals(userId)) {
      throw new ResourceNotFoundException("Identification not found");
    }

    CarePlanDto plan = parseCarePlan(identification.getCarePlan());
    List<CareCardDto> careCards = new ArrayList<>(plan.getCareCards());
    boolean alreadyAdded =
        careCards.stream().anyMatch(card -> req.getRegionLabel().equals(card.getTitle()));

    if (!alreadyAdded) {
      careCards.add(
          CareCardDto.builder()
              .type("PEST")
              .title(req.getRegionLabel())
              .icon("healing")
              .summary("Follow the steps below to treat this issue")
              .detail(req.getAdviceText())
              .urgency("HIGH")
              .actionPlan(ActionPlanValidator.normalize(req.getActionPlan()))
              .build());
      plan.setCareCards(careCards);
      identification.setCarePlan(serializeToJson(plan));
      identificationRepository.save(identification);
      log.info(
          "Care card added: identificationId={}, userId={}, label={}",
          id,
          userId,
          req.getRegionLabel());
    } else {
      plan.setCareCards(careCards);
    }

    return plan;
  }

  @Override
  public SpeciesMatchDto getSpeciesMatch(Long id, Long userId) {
    Identification identification = findOwnedIdentification(id, userId);
    return buildSpeciesMatch(identification.getScientificName(), identification.getCommonName());
  }

  @Override
  @Transactional
  public SpeciesMatchDto resolveSpecies(Long id, ResolveSpeciesRequest req, Long userId) {
    Identification identification = findOwnedIdentification(id, userId);

    if (!req.isConfirmed()) {
      // User rejected the match (or the "new species" suggestion) — leave speciesId unset.
      // Re-scan / manual search is a frontend concern; no search endpoint exists yet.
      return SpeciesMatchDto.builder()
          .matched(false)
          .speciesId(null)
          .scientificName(identification.getScientificName())
          .commonName(identification.getCommonName())
          .build();
    }

    Species species =
        speciesRepository
            .findByScientificName(identification.getScientificName())
            .orElseGet(
                () ->
                    speciesService.findOrCreate(
                        identification.getScientificName(), identification.getCommonName()));

    identification.setSpeciesId(species.getId());
    identificationRepository.save(identification);

    return SpeciesMatchDto.builder()
        .matched(true)
        .speciesId(species.getId())
        .scientificName(species.getScientificName())
        .commonName(species.getCommonName())
        .build();
  }

  @Override
  public PlantMatchDto getPlantMatch(Long id, Long userId) {
    Identification identification = findOwnedIdentification(id, userId);
    if (identification.getSpeciesId() == null) {
      throw new ValidationException("Species must be resolved before matching plants");
    }

    List<Plant> plants =
        plantRepository
            .findAllByUserIdAndSpeciesIdAndStatus(
                userId, identification.getSpeciesId(), PlantStatus.ACTIVE, Pageable.unpaged())
            .getContent();

    List<PlantSummaryDto> candidates =
        plants.stream()
            .map(
                plant ->
                    PlantSummaryDto.builder()
                        .id(plant.getId())
                        .nickname(plant.getNickname())
                        .photoUrl(plant.getPhotoUrl())
                        .build())
            .toList();

    return PlantMatchDto.builder().candidatePlants(candidates).build();
  }

  @Override
  @Transactional
  public IdentificationResponse resolvePlant(Long id, ResolvePlantRequest req, Long userId) {
    Identification identification = findOwnedIdentification(id, userId);

    if (req.getPlantId() != null) {
      Plant plant =
          plantRepository
              .findByIdAndUserId(req.getPlantId(), userId)
              .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));
      identification.setPlantId(plant.getId());
      identificationRepository.save(identification);
      plant.setLastScanId(identification.getId());
      plantRepository.save(plant);
    } else {
      // Reuse the existing nickname-fallback creation flow from T2.8 rather than duplicating it.
      SaveIdentificationAsPlantRequest saveRequest = new SaveIdentificationAsPlantRequest();
      saveRequest.setIdentificationId(id);
      plantService.saveFromIdentification(saveRequest, userId);
    }

    return getIdentification(id, userId);
  }

  private SpeciesMatchDto buildSpeciesMatch(String scientificName, String commonName) {
    return speciesRepository
        .findByScientificName(scientificName)
        .map(
            species ->
                SpeciesMatchDto.builder()
                    .matched(true)
                    .speciesId(species.getId())
                    .scientificName(species.getScientificName())
                    .commonName(species.getCommonName())
                    .build())
        .orElseGet(
            () ->
                SpeciesMatchDto.builder()
                    .matched(false)
                    .speciesId(null)
                    .scientificName(scientificName)
                    .commonName(commonName)
                    .build());
  }

  private Identification findOwnedIdentification(Long id, Long userId) {
    Identification identification =
        identificationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Identification not found"));
    if (!identification.getUserId().equals(userId)) {
      throw new ResourceNotFoundException("Identification not found");
    }
    return identification;
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

  private CureAdviceResponse parseCureAdvice(String raw) {
    try {
      CureAdviceJson parsed = objectMapper.readValue(raw, CureAdviceJson.class);
      return CureAdviceResponse.builder()
          .advice(parsed.getAdvice())
          .actionPlan(ActionPlanValidator.normalize(parsed.getActionPlan()))
          .build();
    } catch (JsonProcessingException e) {
      log.warn(
          "Malformed cure advice JSON, falling back to raw text as advice: {}", e.getMessage());
      return CureAdviceResponse.builder().advice(raw).actionPlan(null).build();
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

  private void normalizeActionPlans(CarePlanDto carePlan) {
    if (carePlan.getCareCards() == null) {
      return;
    }
    for (CareCardDto card : carePlan.getCareCards()) {
      card.setActionPlan(ActionPlanValidator.normalize(card.getActionPlan()));
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

  private boolean consumeCureRateLimit(Long userId) {
    Bucket bucket =
        cureAdviceBuckets.computeIfAbsent(
            userId,
            id ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(CURE_ADVICE_RATE_LIMIT)
                            .refillIntervally(CURE_ADVICE_RATE_LIMIT, Duration.ofHours(1))
                            .build())
                    .build());
    return bucket.tryConsume(1);
  }

  private AiModelPreference loadUserPreference(Long userId) {
    return userRepository
        .findById(userId)
        .map(user -> user.getAiModelPreference())
        .orElse(AiModelPreference.DEEPSEEK);
  }

  /**
   * rawJson is the AI response; providerUsed is the model that actually served the request — may
   * differ from the requested {@link AiModelPreference} when a fallback kicks in (e.g. OLLAMA_LLAVA
   * failing over to GITHUB_GPT4O).
   */
  private record IdentificationOutcome(String rawJson, String providerUsed) {}

  private IdentificationOutcome runIdentification(
      AiModelPreference preference, byte[] imageBytes, String mediaType, List<String> organs) {
    return switch (preference) {
      case PLANTNET ->
          new IdentificationOutcome(
              plantNetToRawResult(
                  plantNetClient.identify(
                      List.of(new ByteArrayMultipartFile(imageBytes, mediaType)),
                      organs != null ? organs : List.of("auto"))),
              AiModelPreference.PLANTNET.name());
      case OLLAMA_LLAVA -> {
        try {
          yield new IdentificationOutcome(
              ollamaClient.identifyPlant(imageBytes, mediaType),
              AiModelPreference.OLLAMA_LLAVA.name());
        } catch (PlantPalException e) {
          log.warn(
              "Ollama identification failed ({}), falling back to GitHubModels", e.getMessage());
          yield new IdentificationOutcome(
              gitHubModelsClient.identifyPlant(imageBytes, mediaType),
              AiModelPreference.GITHUB_GPT4O.name());
        }
      }
      case GITHUB_GPT4O ->
          new IdentificationOutcome(
              gitHubModelsClient.identifyPlant(imageBytes, mediaType),
              AiModelPreference.GITHUB_GPT4O.name());
      default ->
          new IdentificationOutcome(
              gitHubModelsClient.identifyPlant(imageBytes, mediaType),
              AiModelPreference.DEEPSEEK.name());
    };
  }

  private String plantNetToRawResult(PlantNetResponse response) {
    if (response == null || response.results() == null || response.results().isEmpty()) {
      return "{\"species\":null,\"commonName\":\"Unknown Plant\",\"confidence\":\"LOW\","
          + "\"healthStatus\":\"UNKNOWN\",\"healthNotes\":null}";
    }
    PlantNetResult top = response.results().get(0);
    String species =
        top.species() != null ? top.species().scientificNameWithoutAuthor() : "Unknown";
    String commonName = "Unknown Plant";
    if (top.species() != null
        && top.species().commonNames() != null
        && !top.species().commonNames().isEmpty()) {
      commonName = top.species().commonNames().get(0);
    }
    String confidence = top.score() >= 0.7 ? "HIGH" : top.score() >= 0.4 ? "MEDIUM" : "LOW";
    java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
    map.put("species", species);
    map.put("commonName", commonName);
    map.put("confidence", confidence);
    map.put("healthStatus", "UNKNOWN");
    map.put("healthNotes", "PlantNet identifies species only — health analysis not available.");
    return serializeToJson(map);
  }

  private void updatePlantSpecies(
      Long plantId, Long userId, String scientificName, String commonName, Long scanId) {
    plantRepository
        .findByIdAndUserId(plantId, userId)
        .ifPresent(
            plant -> {
              plant.setSpecies(scientificName);
              plant.setCommonName(commonName);
              plant.setLastScanId(scanId);
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
      List<AnnotationRegionDto> parsed =
          objectMapper.convertValue(
              regions,
              objectMapper
                  .getTypeFactory()
                  .constructCollectionType(List.class, AnnotationRegionDto.class));
      parsed.forEach(
          r -> {
            if (r.getPolygon() != null && r.getPolygon().size() < 3) {
              r.setPolygon(null);
            }
          });
      return parsed;
    } catch (Exception e) {
      log.warn("Malformed annotation regions JSON: {}", e.getMessage());
      return List.of();
    }
  }

  private void evictPlantsCache() {
    Cache cache = cacheManager.getCache(PLANTS_CACHE);
    if (cache != null) {
      cache.clear();
    }
  }

  private String resolveMediaType(String photoUrl) {
    if (photoUrl == null) return MediaType.IMAGE_JPEG_VALUE;
    String lower = photoUrl.toLowerCase();
    if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
    if (lower.endsWith(".webp")) return "image/webp";
    return MediaType.IMAGE_JPEG_VALUE;
  }

  private void publishCompletedEvent(Long identificationId, IdentificationStatus status) {
    IdentificationCompletedEvent event =
        IdentificationCompletedEvent.builder()
            .identificationId(identificationId)
            .status(status.name())
            .completedAt(Instant.now())
            .build();
    kafkaTemplate.send(KafkaTopicConfig.IDENTIFICATION_COMPLETED_TOPIC, event);
  }

  /** Wire shape returned by {@link DeepSeekClient#generateCureAdvice}. */
  private static final class CureAdviceJson {
    private String advice;
    private ActionPlanDto actionPlan;

    public String getAdvice() {
      return advice;
    }

    public void setAdvice(String advice) {
      this.advice = advice;
    }

    public ActionPlanDto getActionPlan() {
      return actionPlan;
    }

    public void setActionPlan(ActionPlanDto actionPlan) {
      this.actionPlan = actionPlan;
    }
  }

  /** Adapts raw bytes loaded from storage into a {@link MultipartFile} for PlantNetClient. */
  private static final class ByteArrayMultipartFile implements MultipartFile {
    private final byte[] bytes;
    private final String contentType;

    ByteArrayMultipartFile(byte[] bytes, String contentType) {
      this.bytes = bytes;
      this.contentType = contentType;
    }

    @Override
    public String getName() {
      return "image";
    }

    @Override
    public String getOriginalFilename() {
      return "image.jpg";
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return bytes.length == 0;
    }

    @Override
    public long getSize() {
      return bytes.length;
    }

    @Override
    public byte[] getBytes() {
      return bytes;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) {
      throw new UnsupportedOperationException();
    }
  }
}
