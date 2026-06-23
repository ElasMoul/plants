package com.plantpal.identification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.AnthropicClient;
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
import com.plantpal.identification.dto.PlantNetCandidateDto;
import com.plantpal.identification.dto.PlantNetReferenceImageDto;
import com.plantpal.identification.dto.PlantSummaryDto;
import com.plantpal.identification.dto.ResolvePlantRequest;
import com.plantpal.identification.dto.ResolveSpeciesRequest;
import com.plantpal.identification.dto.SpeciesMatchDto;
import com.plantpal.identification.dto.plantnet.PlantNetReferenceImage;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.event.DuplicateCareCardRemovedEvent;
import com.plantpal.identification.event.IdentificationCompletedEvent;
import com.plantpal.identification.event.IdentificationRequestedEvent;
import com.plantpal.identification.mapper.IdentificationMapper;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.identification.service.IdentificationService;
import com.plantpal.identification.util.ActionPlanValidator;
import com.plantpal.identification.util.LenientJsonParser;
import com.plantpal.plant.dto.SaveIdentificationAsPlantRequest;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.PlantService;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.shared.storage.FileStorageService;
import com.plantpal.shared.util.ImageUtil;
import com.plantpal.species.entity.Species;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesService;
import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.entity.ReasoningModelPreference;
import com.plantpal.user.entity.VisionModelPreference;
import com.plantpal.user.repository.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
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
  private static final double CLOSE_RUNNER_UP_MARGIN = 0.10;

  @Value("${app.plantnet.always-on-candidates:true}")
  private boolean plantNetAlwaysOn;

  @Value("${app.plantnet.project:all}")
  private String plantNetDefaultProject;

  @Value("${app.plantnet.lang:en}")
  private String plantNetDefaultLang;

  @Value("${app.plantnet.auto-confirm-score:0.90}")
  private double autoConfirmScore;

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
  private final AnthropicClient anthropicClient;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final CacheManager cacheManager;
  private final SpeciesRepository speciesRepository;
  private final SpeciesService speciesService;
  private final PlantService plantService;
  private final ApplicationEventPublisher eventPublisher;
  private final Executor aiTaskExecutor;

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
      AnthropicClient anthropicClient,
      KafkaTemplate<String, Object> kafkaTemplate,
      CacheManager cacheManager,
      SpeciesRepository speciesRepository,
      SpeciesService speciesService,
      PlantService plantService,
      ApplicationEventPublisher eventPublisher,
      @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
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
    this.anthropicClient = anthropicClient;
    this.kafkaTemplate = kafkaTemplate;
    this.cacheManager = cacheManager;
    this.speciesRepository = speciesRepository;
    this.speciesService = speciesService;
    this.plantService = plantService;
    this.eventPublisher = eventPublisher;
    this.aiTaskExecutor = aiTaskExecutor;
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
    ConsumptionProbe rateLimitProbe = consumeRateLimit(userId);
    if (!rateLimitProbe.isConsumed()) {
      throw new RateLimitException(
          "AI identification rate limit reached — try again later",
          retryAfterSeconds(rateLimitProbe));
    }

    // Step 4: Publish event for async processing by the Kafka consumer
    VisionModelPreference preference = loadVisionPreference(userId);
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
      VisionModelPreference preference = parseVisionPreference(event.getAiModelPreference());
      Long plantId = identification.getPlantId();
      Long userId = identification.getUserId();

      // Normalize once so the stored dimensions match what every AI provider actually sees.
      byte[] imageBytes = ImageUtil.resizeAndConvertToJpeg(rawBytes, SOURCE_IMAGE_MAX_SIDE_PX);
      int[] dims = ImageUtil.readDimensions(imageBytes);
      identification.setSourceImageWidth(dims[0]);
      identification.setSourceImageHeight(dims[1]);

      // Fire identification + annotation (+ optional always-on PlantNet) in parallel.
      // The always-on PlantNet call runs even when the primary model is not PLANTNET so that
      // the species-confirm step (T8.2) always has a ranked candidate list to show. It swallows
      // its own exceptions — a PlantNet failure must never fail the main identification.
      final List<String> organsForParallelCall = event.getOrgans();
      CompletableFuture<IdentificationOutcome> identificationFuture =
          CompletableFuture.supplyAsync(
              () -> runIdentification(preference, imageBytes, mediaType, event.getOrgans()));
      CompletableFuture<String> annotationFuture =
          CompletableFuture.supplyAsync(
              () -> visionAnnotationClient.analyzeRegions(imageBytes, mediaType));
      CompletableFuture<PlantNetResponse> alwaysOnPlantNetFuture =
          (plantNetAlwaysOn && preference != VisionModelPreference.PLANTNET)
              ? CompletableFuture.supplyAsync(
                  () -> {
                    try {
                      return plantNetClient.identify(
                          List.of(new ByteArrayMultipartFile(imageBytes, mediaType)),
                          organsForParallelCall != null ? organsForParallelCall : List.of("auto"),
                          plantNetDefaultProject,
                          plantNetDefaultLang);
                    } catch (Exception e) {
                      log.warn(
                          "Always-on PlantNet call failed, continuing without candidates: {}",
                          e.getMessage());
                      return null;
                    }
                  },
                  aiTaskExecutor)
              : CompletableFuture.completedFuture(null);

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

      // PlantNet candidates: from the PLANTNET-primary outcome OR from the always-on parallel call
      PlantNetResponse plantNetResponse =
          outcome.plantNetResponse() != null
              ? outcome.plantNetResponse()
              : alwaysOnPlantNetFuture.join();

      // Parse combined result; fall back gracefully if AI JSON is malformed
      DeepSeekPlantResult result = parseIdentificationResult(rawResult);
      CarePlanDto carePlan =
          result.getCarePlan() != null ? result.getCarePlan() : fallbackCarePlan();
      carePlan.setGeneratedByModel(outcome.providerUsed());
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
      if (plantNetResponse != null) {
        identification.setPlantnetCandidates(serializeToJson(mapToCandidateDtos(plantNetResponse)));
        identification.setPlantnetVersion(plantNetResponse.version());
        identification.setPlantnetBestMatch(plantNetResponse.bestMatch());
        identification.setPlantnetSwitchToProject(plantNetResponse.switchToProject());
        identification.setPlantnetQuotaRemaining(
            plantNetResponse.remainingIdentificationRequests());
      }
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
    response.setPlantNetCandidates(parsePlantNetCandidates(entity.getPlantnetCandidates()));
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
              resp.setPlantNetCandidates(parsePlantNetCandidates(entity.getPlantnetCandidates()));
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
              resp.setPlantNetCandidates(parsePlantNetCandidates(entity.getPlantnetCandidates()));
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
    ConsumptionProbe cureRateLimitProbe = consumeCureRateLimit(userId);
    if (!cureRateLimitProbe.isConsumed()) {
      throw new RateLimitException(
          "Cure advice rate limit reached — try again later",
          retryAfterSeconds(cureRateLimitProbe));
    }
    ReasoningModelPreference preference = loadReasoningPreference(userId);
    String raw =
        generateCureAdviceForPreference(preference, req.getSpecies(), req.getRegionLabel());
    return CompletableFuture.completedFuture(parseCureAdvice(raw, preference));
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
              .actionPlanModel(
                  req.getReasoningModelUsed() != null
                      ? req.getReasoningModelUsed()
                      : ReasoningModelPreference.DEEPSEEK_R1.name())
              .build());
      plan.setCareCards(careCards);
      identification.setCarePlan(serializeToJson(plan));
      identificationRepository.save(identification);
      log.info(
          "Care card added: identificationId={}, userId={}, label={}",
          id,
          userId,
          req.getRegionLabel());
      fireDuplicateCareCardCheck(identification.getPlantId(), userId);
    } else {
      plan.setCareCards(careCards);
    }

    return plan;
  }

  /**
   * Fires a background check across every Identification belonging to this plant for PEST care
   * cards that describe the same disease/pest as one just added — each scan creates its own
   * Identification row with its own care_plan JSONB, so the in-row dedup check in {@link
   * #addCareCard} never sees cards added from a previous scan. Same-class self-invocation, so this
   * uses the raw executor directly rather than {@code @Async} (the Spring proxy has no effect on
   * self-invocation).
   */
  private void fireDuplicateCareCardCheck(Long plantId, Long userId) {
    if (plantId == null) return;
    CompletableFuture.runAsync(() -> verifyNoDuplicateCareCards(plantId, userId), aiTaskExecutor);
  }

  private record CareCardRef(Long identificationId, CareCardDto card) {}

  private void verifyNoDuplicateCareCards(Long plantId, Long userId) {
    try {
      List<Identification> identifications =
          identificationRepository
              .findByPlantIdOrderByCreatedAtDesc(plantId, Pageable.unpaged())
              .getContent();

      // Newest-first (matches the query order) so the first ref encountered per duplicate group
      // is always the one to keep.
      List<CareCardRef> refs = new ArrayList<>();
      for (Identification ident : identifications) {
        CarePlanDto plan = parseCarePlan(ident.getCarePlan());
        if (plan.getCareCards() == null) continue;
        for (CareCardDto card : plan.getCareCards()) {
          if ("PEST".equals(card.getType())) {
            refs.add(new CareCardRef(ident.getId(), card));
          }
        }
      }
      if (refs.size() < 2) return;

      List<Map<String, String>> summaries = new ArrayList<>();
      for (int i = 0; i < refs.size(); i++) {
        CareCardDto card = refs.get(i).card();
        summaries.add(
            Map.of(
                "ref",
                String.valueOf(i),
                "title",
                card.getTitle() != null ? card.getTitle() : "",
                "detail",
                card.getDetail() != null ? card.getDetail() : ""));
      }

      String raw = deepSeekClient.detectDuplicateCareCards(serializeToJson(summaries));
      List<List<Integer>> groups = parseDuplicateGroups(raw);

      for (List<Integer> group : groups) {
        if (group.size() < 2) continue;
        int keepIndex = group.stream().min(Integer::compareTo).orElseThrow();
        String diseaseName = refs.get(keepIndex).card().getTitle();
        for (int idx : group) {
          if (idx == keepIndex) continue;
          removeCareCard(refs.get(idx));
          eventPublisher.publishEvent(new DuplicateCareCardRemovedEvent(plantId, diseaseName));
        }
      }
    } catch (PlantPalException e) {
      log.warn(
          "Duplicate care card check failed, leaving cards as-is: plantId={}, error={}",
          plantId,
          e.getMessage());
    }
  }

  private void removeCareCard(CareCardRef ref) {
    identificationRepository
        .findById(ref.identificationId())
        .ifPresent(
            ident -> {
              CarePlanDto plan = parseCarePlan(ident.getCarePlan());
              List<CareCardDto> cards = new ArrayList<>(plan.getCareCards());
              boolean removed =
                  cards.removeIf(
                      c ->
                          "PEST".equals(c.getType())
                              && ref.card().getTitle() != null
                              && ref.card().getTitle().equals(c.getTitle()));
              if (!removed) return;
              plan.setCareCards(cards);
              ident.setCarePlan(serializeToJson(plan));
              identificationRepository.save(ident);
              log.info(
                  "Removed duplicate care card: identificationId={}, title={}",
                  ident.getId(),
                  ref.card().getTitle());
            });
  }

  private List<List<Integer>> parseDuplicateGroups(String raw) {
    try {
      DuplicateGroupsJson parsed = objectMapper.readValue(raw, DuplicateGroupsJson.class);
      if (parsed.getDuplicateGroups() == null) return List.of();
      List<List<Integer>> result = new ArrayList<>();
      for (List<String> group : parsed.getDuplicateGroups()) {
        List<Integer> indices = new ArrayList<>();
        for (String ref : group) {
          try {
            indices.add(Integer.parseInt(ref.trim()));
          } catch (NumberFormatException ignored) {
            // Malformed ref from the AI — skip it rather than fail the whole group.
          }
        }
        result.add(indices);
      }
      return result;
    } catch (JsonProcessingException e) {
      log.warn("Malformed duplicate care card groups JSON: {}", e.getMessage());
      return List.of();
    }
  }

  @Override
  public SpeciesMatchDto getSpeciesMatch(Long id, Long userId) {
    Identification identification = findOwnedIdentification(id, userId);
    List<PlantNetCandidateDto> candidates =
        parsePlantNetCandidates(identification.getPlantnetCandidates());
    if (!candidates.isEmpty()) {
      return buildSpeciesMatchFromCandidates(identification, candidates);
    }
    return buildSpeciesMatch(identification.getScientificName(), identification.getCommonName());
  }

  @Override
  @Transactional
  public SpeciesMatchDto resolveSpecies(Long id, ResolveSpeciesRequest req, Long userId) {
    Identification identification = findOwnedIdentification(id, userId);

    if (!req.isConfirmed()) {
      // User rejected the match — leave speciesId unset; re-scan is a frontend concern.
      return SpeciesMatchDto.builder()
          .matched(false)
          .speciesId(null)
          .scientificName(identification.getScientificName())
          .commonName(identification.getCommonName())
          .build();
    }

    // Prefer the user's explicit candidate choice; fall back to what the AI identified.
    String scientificName =
        req.getChosenScientificName() != null && !req.getChosenScientificName().isBlank()
            ? req.getChosenScientificName()
            : identification.getScientificName();

    if (scientificName == null || scientificName.isBlank()) {
      throw new ValidationException(
          "Cannot save this species — the AI couldn't determine a scientific name for this scan."
              + " Please re-scan.");
    }

    // Resolve the matching PlantNet candidate to obtain common name + factual taxonomy IDs.
    PlantNetCandidateDto chosenCandidate =
        parsePlantNetCandidates(identification.getPlantnetCandidates()).stream()
            .filter(c -> scientificName.equals(c.getScientificName()))
            .findFirst()
            .orElse(null);

    String commonName =
        chosenCandidate != null
            ? firstCommonName(chosenCandidate.getCommonNames())
            : identification.getCommonName();
    String gbifId = chosenCandidate != null ? chosenCandidate.getGbifId() : null;
    String powoId = chosenCandidate != null ? chosenCandidate.getPowoId() : null;
    String iucnCategory = chosenCandidate != null ? chosenCandidate.getIucnCategory() : null;

    Species species =
        speciesService.findOrCreate(
            scientificName,
            commonName,
            toLegacyReasoningPreference(loadReasoningPreference(userId)),
            gbifId,
            powoId,
            iucnCategory);

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

  private SpeciesMatchDto buildSpeciesMatchFromCandidates(
      Identification identification, List<PlantNetCandidateDto> candidates) {
    PlantNetCandidateDto top = candidates.get(0);
    boolean autoConfirmable =
        top.getScore() >= autoConfirmScore
            && (candidates.size() == 1
                || top.getScore() - candidates.get(1).getScore() > CLOSE_RUNNER_UP_MARGIN);

    Optional<Species> existingSpecies =
        top.getScientificName() != null
            ? speciesRepository.findByScientificName(top.getScientificName())
            : Optional.empty();

    return SpeciesMatchDto.builder()
        .matched(existingSpecies.isPresent())
        .speciesId(existingSpecies.map(Species::getId).orElse(null))
        .scientificName(top.getScientificName())
        .commonName(firstCommonName(top.getCommonNames()))
        .candidates(candidates)
        .bestMatch(identification.getPlantnetBestMatch())
        .switchToProject(identification.getPlantnetSwitchToProject())
        .autoConfirmable(autoConfirmable)
        .plantNetVersion(identification.getPlantnetVersion())
        .build();
  }

  private static String firstCommonName(List<String> names) {
    return names != null && !names.isEmpty() ? names.get(0) : null;
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

  private CureAdviceResponse parseCureAdvice(String raw, ReasoningModelPreference preference) {
    try {
      CureAdviceJson parsed = objectMapper.readValue(raw, CureAdviceJson.class);
      return CureAdviceResponse.builder()
          .advice(parsed.getAdvice())
          .actionPlan(ActionPlanValidator.normalize(parsed.getActionPlan()))
          .reasoningModelUsed(preference.name())
          .build();
    } catch (JsonProcessingException e) {
      CureAdviceJson merged = parseConcatenatedCureAdviceJson(raw);
      if (merged != null) {
        return CureAdviceResponse.builder()
            .advice(merged.getAdvice())
            .actionPlan(ActionPlanValidator.normalize(merged.getActionPlan()))
            .reasoningModelUsed(preference.name())
            .build();
      }
      log.warn(
          "Malformed cure advice JSON, falling back to raw text as advice: {}", e.getMessage());
      return CureAdviceResponse.builder()
          .advice(raw)
          .actionPlan(null)
          .reasoningModelUsed(preference.name())
          .build();
    }
  }

  /**
   * Some local models (Ollama/llava-phi3) emit two sibling JSON objects for this prompt instead of
   * one combined object (e.g. {@code {"advice":"..."}{"actionPlan":{...}}}) — see {@link
   * LenientJsonParser}. Returns null (never throws) if the content isn't JSON at all, so the caller
   * can fall back to showing the raw text.
   */
  private CureAdviceJson parseConcatenatedCureAdviceJson(String raw) {
    JsonNode merged = LenientJsonParser.mergeConcatenatedObjects(objectMapper, raw);
    if (merged == null) {
      return null;
    }
    try {
      return objectMapper.treeToValue(merged, CureAdviceJson.class);
    } catch (JsonProcessingException e) {
      return null;
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

  private ConsumptionProbe consumeRateLimit(Long userId) {
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
    return bucket.tryConsumeAndReturnRemaining(1);
  }

  private ConsumptionProbe consumeCureRateLimit(Long userId) {
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
    return bucket.tryConsumeAndReturnRemaining(1);
  }

  private static long retryAfterSeconds(ConsumptionProbe probe) {
    return Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();
  }

  private VisionModelPreference loadVisionPreference(Long userId) {
    return userRepository
        .findById(userId)
        .map(user -> user.getVisionModelPreference())
        .orElse(VisionModelPreference.GITHUB_GPT4O);
  }

  private ReasoningModelPreference loadReasoningPreference(Long userId) {
    return userRepository
        .findById(userId)
        .map(user -> user.getReasoningModelPreference())
        .orElse(ReasoningModelPreference.DEEPSEEK_R1);
  }

  private String generateCureAdviceForPreference(
      ReasoningModelPreference preference, String species, String regionLabel) {
    return switch (preference) {
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 -> ollamaClient.generateCureAdvice(species, regionLabel);
      case ANTHROPIC_CLAUDE -> anthropicClient.generateCureAdvice(species, regionLabel);
      case GITHUB_O4_MINI -> deepSeekClient.generateCureAdviceViaO4Mini(species, regionLabel);
      case GITHUB_GPT41_MINI -> deepSeekClient.generateCureAdviceViaGpt41Mini(species, regionLabel);
      case DEEPSEEK_R1 -> deepSeekClient.generateCureAdvice(species, regionLabel);
    };
  }

  /**
   * {@link com.plantpal.species.service.SpeciesService#findOrCreate} still takes the legacy {@link
   * AiModelPreference} (only OLLAMA_LLAVA vs. everything-else-uses-DeepSeek matters to it) — map
   * the real reasoning preference onto it rather than reading the stale stored field, so species
   * enrichment actually respects the user's current choice.
   */
  private static AiModelPreference toLegacyReasoningPreference(
      ReasoningModelPreference preference) {
    return switch (preference) {
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 -> AiModelPreference.OLLAMA_LLAVA;
      case DEEPSEEK_R1, GITHUB_O4_MINI, GITHUB_GPT41_MINI, ANTHROPIC_CLAUDE ->
          AiModelPreference.DEEPSEEK;
    };
  }

  /**
   * Defensive: a stale/legacy preference string (e.g. a Kafka message enqueued before this
   * deployment, or a value like the old "DEEPSEEK" which was never a real vision model) falls back
   * to the default vision model instead of crashing the consumer.
   */
  private static VisionModelPreference parseVisionPreference(String raw) {
    try {
      return VisionModelPreference.valueOf(raw);
    } catch (IllegalArgumentException | NullPointerException e) {
      return VisionModelPreference.GITHUB_GPT4O;
    }
  }

  /**
   * rawJson is the AI response; providerUsed is the model that actually served the request.
   * plantNetResponse is non-null only when preference==PLANTNET (carries the full ranked list).
   */
  private record IdentificationOutcome(
      String rawJson, String providerUsed, PlantNetResponse plantNetResponse) {
    IdentificationOutcome(String rawJson, String providerUsed) {
      this(rawJson, providerUsed, null);
    }
  }

  private IdentificationOutcome runIdentification(
      VisionModelPreference preference, byte[] imageBytes, String mediaType, List<String> organs) {
    return switch (preference) {
      case PLANTNET -> {
        PlantNetResponse pnr =
            plantNetClient.identify(
                List.of(new ByteArrayMultipartFile(imageBytes, mediaType)),
                organs != null ? organs : List.of("auto"),
                plantNetDefaultProject,
                plantNetDefaultLang);
        yield new IdentificationOutcome(
            plantNetToRawResult(pnr), VisionModelPreference.PLANTNET.name(), pnr);
      }
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 ->
          new IdentificationOutcome(
              ollamaClient.identifyPlant(imageBytes, mediaType),
              VisionModelPreference.OLLAMA_GEMMA3.name());
      case GITHUB_GPT4O ->
          new IdentificationOutcome(
              gitHubModelsClient.identifyPlant(imageBytes, mediaType),
              VisionModelPreference.GITHUB_GPT4O.name());
      case GITHUB_GPT41 ->
          new IdentificationOutcome(
              gitHubModelsClient.identifyPlantWithGpt41(imageBytes, mediaType),
              VisionModelPreference.GITHUB_GPT41.name());
      case ANTHROPIC_CLAUDE ->
          new IdentificationOutcome(
              anthropicClient.identifyPlant(imageBytes, mediaType),
              VisionModelPreference.ANTHROPIC_CLAUDE.name());
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

  private List<PlantNetCandidateDto> mapToCandidateDtos(PlantNetResponse response) {
    if (response == null || response.results() == null) return List.of();
    return response.results().stream()
        .map(
            result -> {
              String scientificName =
                  result.species() != null ? result.species().scientificNameWithoutAuthor() : null;
              String genus =
                  result.species() != null && result.species().genus() != null
                      ? result.species().genus().scientificNameWithoutAuthor()
                      : null;
              String family =
                  result.species() != null && result.species().family() != null
                      ? result.species().family().scientificNameWithoutAuthor()
                      : null;
              List<String> commonNames =
                  result.species() != null ? result.species().commonNames() : List.of();
              String gbifId = result.gbif() != null ? result.gbif().id() : null;
              String powoId = result.powo() != null ? result.powo().id() : null;
              String iucnCategory = result.iucn() != null ? result.iucn().category() : null;
              List<PlantNetReferenceImageDto> refImages =
                  PlantNetClient.safeReferenceImages(result).stream()
                      .map(this::toReferenceImageDto)
                      .toList();
              return PlantNetCandidateDto.builder()
                  .score(result.score())
                  .scientificName(scientificName)
                  .genus(genus)
                  .family(family)
                  .commonNames(commonNames)
                  .gbifId(gbifId)
                  .powoId(powoId)
                  .iucnCategory(iucnCategory)
                  .referenceImages(refImages)
                  .build();
            })
        .toList();
  }

  private PlantNetReferenceImageDto toReferenceImageDto(PlantNetReferenceImage img) {
    String smallUrl = img.url() != null ? img.url().s() : null;
    String mediumUrl = img.url() != null ? img.url().m() : null;
    return PlantNetReferenceImageDto.builder()
        .smallUrl(smallUrl)
        .mediumUrl(mediumUrl)
        .author(img.author())
        .license(img.license())
        .citation(img.citation())
        .build();
  }

  private List<PlantNetCandidateDto> parsePlantNetCandidates(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(
          json,
          objectMapper
              .getTypeFactory()
              .constructCollectionType(List.class, PlantNetCandidateDto.class));
    } catch (JsonProcessingException e) {
      log.warn("Malformed plantnet_candidates JSON: {}", e.getMessage());
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

  /** Wire shape returned by {@link DeepSeekClient#detectDuplicateCareCards}. */
  private static final class DuplicateGroupsJson {
    private List<List<String>> duplicateGroups;

    public List<List<String>> getDuplicateGroups() {
      return duplicateGroups;
    }

    public void setDuplicateGroups(List<List<String>> duplicateGroups) {
      this.duplicateGroups = duplicateGroups;
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
