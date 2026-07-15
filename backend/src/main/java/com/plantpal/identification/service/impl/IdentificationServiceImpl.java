package com.plantpal.identification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.gateway.GatewayClient;
import com.plantpal.gateway.GatewayProperties;
import com.plantpal.gateway.PlantNetGatewayClient;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.client.PlantNetDiseaseClient;
import com.plantpal.identification.client.VisionAnnotationClient;
import com.plantpal.identification.config.KafkaTopicConfig;
import com.plantpal.identification.dispatch.IdentificationDispatcher;
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
import com.plantpal.identification.dto.plantnet.PlantNetDiseaseResponse;
import com.plantpal.identification.dto.plantnet.PlantNetReferenceImage;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStageStatus;
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
import com.plantpal.shared.config.KafkaTransportProperties;
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
import io.platform.contracts.aigateway.AiRequest;
import io.platform.contracts.aigateway.AiRequestMediaInner;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdentificationServiceImpl implements IdentificationService {

  private static final Logger log = LoggerFactory.getLogger(IdentificationServiceImpl.class);

  private static final int MAX_IMAGES = 5;
  private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
  private static final int CURE_ADVICE_RATE_LIMIT = 10;
  private static final int SOURCE_IMAGE_MAX_SIDE_PX = 1024;
  private static final String PLANTS_CACHE = "plants";
  private static final List<String> ALLOWED_TYPES =
      List.of("image/jpeg", "image/png", "image/webp");
  private static final double CLOSE_RUNNER_UP_MARGIN = 0.10;

  // Some providers (and the ai-gateway passthrough path, which bypasses each client's own
  // stripThinkTags()) ignore response_format and wrap JSON in a markdown code fence, or precede it
  // with prose. Matches a ```json / ``` / ~~~ fenced block anywhere in the string (see
  // extractJson()).
  private static final Pattern FENCED_JSON =
      Pattern.compile("(?:```|~~~)(?:json)?\\s*(.*?)\\s*(?:```|~~~)", Pattern.DOTALL);

  // ai-gateway's AnthropicAdapter defaults max_tokens to 2048 when the request context carries no
  // "maxTokens" entry (ctx.containsKey("maxTokens") ? ... : 2048) — half the budget the direct
  // clients use (AnthropicClient.DEFAULT_MAX_TOKENS / DeepSeekClient.O4_MINI_MAX_COMPLETION_TOKENS
  // are both 4096), which was silently truncating gateway-routed structured-JSON responses mid
  // array and falling back to "Unknown Plant". Every gateway AiRequest whose response is parsed as
  // JSON must set this explicitly; never go below the direct-path floor of 4096.
  private static final int GATEWAY_MAX_TOKENS = 4096;

  // The main identification response nests species/confidence/health fields plus a full
  // multi-card care plan (each card carrying its own nested actionPlan) — comfortably larger than
  // the baseline reasoning/annotation payloads below, so it gets extra headroom above
  // GATEWAY_MAX_TOKENS instead of sharing the bare 4096 floor.
  private static final int GATEWAY_IDENTIFICATION_MAX_TOKENS = 8192;

  @Value("${app.plantnet.always-on-candidates:true}")
  private boolean plantNetAlwaysOn;

  @Value("${app.plantnet.project:all}")
  private String plantNetDefaultProject;

  @Value("${app.plantnet.lang:en}")
  private String plantNetDefaultLang;

  @Value("${app.plantnet.auto-confirm-score:0.90}")
  private double autoConfirmScore;

  // T-DEPLOY.3: wires the previously-dead app.rate-limit.ai-calls-per-hour config key. Semantics
  // match exactly — this gates submitIdentification()'s per-user AI identification calls/hour,
  // which is what that key has always described (see application.yml).
  @Value("${app.rate-limit.ai-calls-per-hour:20}")
  private int aiCallsPerHour;

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
  private final PlantNetDiseaseClient plantNetDiseaseClient;
  private final OllamaClient ollamaClient;
  private final AnthropicClient anthropicClient;
  private final IdentificationDispatcher identificationDispatcher;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final KafkaTransportProperties kafkaTransportProperties;
  private final CacheManager cacheManager;
  private final SpeciesRepository speciesRepository;
  private final SpeciesService speciesService;
  private final PlantService plantService;
  private final ApplicationEventPublisher eventPublisher;
  private final Executor aiTaskExecutor;
  private final GatewayClient gatewayClient;
  private final GatewayProperties gatewayProperties;
  private final PlantNetGatewayClient plantNetGatewayClient;

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
      PlantNetDiseaseClient plantNetDiseaseClient,
      OllamaClient ollamaClient,
      AnthropicClient anthropicClient,
      IdentificationDispatcher identificationDispatcher,
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaTransportProperties kafkaTransportProperties,
      CacheManager cacheManager,
      SpeciesRepository speciesRepository,
      SpeciesService speciesService,
      PlantService plantService,
      ApplicationEventPublisher eventPublisher,
      @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
      GatewayClient gatewayClient,
      GatewayProperties gatewayProperties,
      PlantNetGatewayClient plantNetGatewayClient) {
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
    this.plantNetDiseaseClient = plantNetDiseaseClient;
    this.ollamaClient = ollamaClient;
    this.anthropicClient = anthropicClient;
    this.identificationDispatcher = identificationDispatcher;
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaTransportProperties = kafkaTransportProperties;
    this.cacheManager = cacheManager;
    this.speciesRepository = speciesRepository;
    this.speciesService = speciesService;
    this.plantService = plantService;
    this.eventPublisher = eventPublisher;
    this.aiTaskExecutor = aiTaskExecutor;
    this.gatewayClient = gatewayClient;
    this.gatewayProperties = gatewayProperties;
    this.plantNetGatewayClient = plantNetGatewayClient;
  }

  @Override
  public CompletableFuture<IdentificationPendingResponse> submitIdentification(
      List<MultipartFile> images,
      Long plantId,
      Long speciesId,
      Long userId,
      List<String> organs,
      String userContext) {

    validateImages(images);

    // Step 1: Save photos, collect URLs
    List<String> photoUrls = new ArrayList<>();
    for (MultipartFile image : images) {
      photoUrls.add(fileStorageService.savePhoto(image));
    }

    // Step 2: Persist with PENDING status (no AI call yet)
    // speciesId is only ever passed by Flow 2 (scan from a Species page) — Flow 1 (Garden FAB)
    // leaves it null and resolves species after the AI result comes back (see resolveSpecies()).
    String trimmedContext =
        (userContext != null && !userContext.isBlank()) ? userContext.trim() : null;
    Identification identification =
        Identification.builder()
            .userId(userId)
            .plantId(plantId)
            .speciesId(speciesId)
            .photoUrl(photoUrls.get(0))
            .userContext(trimmedContext)
            .status(IdentificationStatus.PENDING)
            .identificationStatus(IdentificationStageStatus.PENDING)
            .annotationStatus(IdentificationStageStatus.PENDING)
            .candidateStatus(IdentificationStageStatus.PENDING)
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

    // Step 3.5: Global executor backstop — fail before Kafka publish if queue is saturated
    if (aiTaskExecutor instanceof ThreadPoolTaskExecutor tpte
        && tpte.getThreadPoolExecutor().getQueue().remainingCapacity() == 0) {
      throw new RateLimitException("Server is busy — too many concurrent identifications", 60L);
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
            .userContext(trimmedContext)
            .build();
    identificationDispatcher.dispatch(event);
    log.info("Dispatched IdentificationRequestedEvent: id={}", identification.getId());

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
      identification.setIdentificationModel(preference.name());

      // Load user's PlantNet flora + lang preferences (T8.4). Falls back to app-level defaults
      // so the always-on call and PLANTNET-primary path both respect the per-user choice.
      String[] pnPrefs = loadPlantNetPreferences(userId);
      final String userPlantNetProject = pnPrefs[0];
      final String userPlantNetLang = pnPrefs[1];

      // Fire identification and disease cross-check in parallel. Annotation is deferred until
      // after we know healthStatus — D10.1 (conservative): skip annotation entirely when the
      // plant is HEALTHY or UNKNOWN, halving annotation API calls for healthy scans.
      final List<String> organsForParallelCall = event.getOrgans();
      final String userContext = event.getUserContext();
      CompletableFuture<IdentificationOutcome> identificationFuture =
          CompletableFuture.supplyAsync(
              () ->
                  runIdentification(
                      preference,
                      imageBytes,
                      mediaType,
                      event.getOrgans(),
                      userPlantNetProject,
                      userPlantNetLang,
                      userContext,
                      userId));

      // Disease cross-check (Flow 3 only — health scan for an existing plant). Runs in parallel;
      // all exceptions are swallowed so a PlantNet outage never fails the main identification.
      CompletableFuture<PlantNetDiseaseResponse> diseaseCheckFuture =
          (plantId != null)
              ? CompletableFuture.supplyAsync(
                  () -> {
                    try {
                      // G4 follow-up: swap the direct PlantNet disease cross-check for the
                      // gateway's /ai/plantnet/disease-check endpoint when the gateway is enabled
                      // (D022) — same best-effort semantics either way (never fails
                      // identification).
                      if (gatewayProperties.enabled()) {
                        return plantNetGatewayClient.checkDisease(
                            imageBytes,
                            mediaType,
                            organsForParallelCall != null ? organsForParallelCall : List.of("auto"),
                            userPlantNetLang);
                      }
                      return plantNetDiseaseClient.identifyDisease(
                          List.of(new ByteArrayMultipartFile(imageBytes, mediaType)),
                          organsForParallelCall != null ? organsForParallelCall : List.of("auto"),
                          userPlantNetLang);
                    } catch (Exception e) {
                      log.warn(
                          "PlantNet disease cross-check failed, continuing without it: {}",
                          e.getMessage());
                      return new PlantNetDiseaseResponse(List.of(), 0);
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

      // D10.1 — run annotation only when disease/pest issues were detected; skip for healthy
      // or unknown health (saves ~50% of annotation API calls). Annotation failure remains
      // non-fatal: sets annotationStatus=FAILED and continues.
      DeepSeekPlantResult resultForAnnotationCheck = parseIdentificationResult(rawResult);
      String healthStatusForAnnotation = resultForAnnotationCheck.getHealthStatus();
      String annotationJson;
      if ("ISSUES_DETECTED".equals(healthStatusForAnnotation)) {
        try {
          annotationJson = runAnnotation(imageBytes, mediaType, userId);
          identification.setAnnotationStatus(IdentificationStageStatus.COMPLETED);
          identification.setAnnotationModel("gpt-4o-mini");
        } catch (Exception e) {
          log.warn(
              "Annotation stage failed for identification id={}: {}",
              identification.getId(),
              e.getMessage());
          identification.setAnnotationStatus(IdentificationStageStatus.FAILED);
          annotationJson = null;
        }
      } else {
        log.info(
            "Annotation skipped for identification id={}: healthStatus={} (D10.1)",
            identification.getId(),
            healthStatusForAnnotation);
        identification.setAnnotationStatus(IdentificationStageStatus.SKIPPED);
        annotationJson = null;
      }

      // PlantNet candidates only from the PLANTNET-primary outcome (always-on enrichment is
      // deferred to fire-and-forget after the core save — see enrichWithPlantNetCandidates).
      PlantNetResponse plantNetResponse = outcome.plantNetResponse();

      // Reuse the result already parsed for the annotation-skip check above.
      DeepSeekPlantResult result = resultForAnnotationCheck;
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
      identification.setIdentificationStatus(IdentificationStageStatus.COMPLETED);
      identification.setStatus(IdentificationStatus.COMPLETED);
      if (plantNetResponse != null) {
        identification.setPlantnetCandidates(serializeToJson(mapToCandidateDtos(plantNetResponse)));
        identification.setPlantnetVersion(plantNetResponse.version());
        identification.setPlantnetBestMatch(plantNetResponse.bestMatch());
        identification.setPlantnetSwitchToProject(plantNetResponse.switchToProject());
        identification.setPlantnetQuotaRemaining(
            plantNetResponse.remainingIdentificationRequests());
      }
      if (preference == VisionModelPreference.PLANTNET) {
        // PlantNet WAS the core call — result is already in the outcome.
        identification.setCandidateStatus(IdentificationStageStatus.COMPLETED);
      } else if (!plantNetAlwaysOn) {
        identification.setCandidateStatus(IdentificationStageStatus.SKIPPED);
      } else {
        // Always-on enrichment is deferred async — mark PENDING until it completes.
        identification.setCandidateStatus(IdentificationStageStatus.PENDING);
      }
      PlantNetDiseaseResponse diseaseResponse = diseaseCheckFuture.join();
      if (diseaseResponse != null && !diseaseResponse.results().isEmpty()) {
        identification.setPlantnetDiseaseResults(serializeToJson(diseaseResponse.results()));
        identification.setPlantnetDiseaseQuotaRemaining(
            diseaseResponse.remainingIdentificationRequests());
      }
      identification = identificationRepository.save(identification);
      evictPlantsCache();

      // Fire deferred PlantNet candidate enrichment AFTER the core result is saved (D1 amendment).
      // Candidates appear in the frontend when candidateStatus transitions PENDING → COMPLETED via
      // the existing 3s poll — no new polling needed.
      if (plantNetAlwaysOn && preference != VisionModelPreference.PLANTNET) {
        final long savedId = identification.getId();
        final byte[] finalImageBytes = imageBytes;
        final String finalMediaType = mediaType;
        final List<String> finalOrgans = organsForParallelCall;
        final String finalProject = userPlantNetProject;
        final String finalLang = userPlantNetLang;
        CompletableFuture.runAsync(
            () ->
                enrichWithPlantNetCandidates(
                    savedId, finalImageBytes, finalMediaType, finalOrgans, finalProject, finalLang),
            aiTaskExecutor);
      }

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
      identification.setIdentificationStatus(IdentificationStageStatus.FAILED);
      identification.setAnnotationStatus(IdentificationStageStatus.SKIPPED);
      identification.setCandidateStatus(IdentificationStageStatus.SKIPPED);
      identification.setFailureReason(classifyFailureReason(e));
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
        generateCureAdviceForPreference(preference, req.getSpecies(), req.getRegionLabel(), userId);
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
  /**
   * Fire-and-forget PlantNet candidate enrichment run after the core identification saves as
   * COMPLETED. Mirrors SpeciesEnrichmentServiceImpl.enrich() — never throws, always updates
   * candidateStatus. Same-class self-invocation: uses the raw executor, not @Async.
   */
  private void enrichWithPlantNetCandidates(
      Long identificationId,
      byte[] imageBytes,
      String mediaType,
      List<String> organs,
      String project,
      String lang) {
    try {
      PlantNetResponse response =
          plantNetClient.identify(
              List.of(new ByteArrayMultipartFile(imageBytes, mediaType)),
              organs != null ? organs : List.of("auto"),
              project,
              lang);
      identificationRepository
          .findById(identificationId)
          .ifPresent(
              ident -> {
                ident.setPlantnetCandidates(serializeToJson(mapToCandidateDtos(response)));
                ident.setPlantnetVersion(response.version());
                ident.setPlantnetBestMatch(response.bestMatch());
                ident.setPlantnetSwitchToProject(response.switchToProject());
                ident.setPlantnetQuotaRemaining(response.remainingIdentificationRequests());
                ident.setCandidateStatus(IdentificationStageStatus.COMPLETED);
                identificationRepository.save(ident);
                log.info(
                    "PlantNet candidate enrichment completed: identificationId={}",
                    identificationId);
              });
    } catch (Exception e) {
      log.warn(
          "PlantNet candidate enrichment failed: identificationId={}, error={}",
          identificationId,
          e.getMessage());
      identificationRepository
          .findById(identificationId)
          .ifPresent(
              ident -> {
                ident.setCandidateStatus(IdentificationStageStatus.FAILED);
                identificationRepository.save(ident);
              });
    }
  }

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
      DuplicateGroupsJson parsed =
          objectMapper.readValue(extractJson(raw), DuplicateGroupsJson.class);
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
    String family = chosenCandidate != null ? chosenCandidate.getFamily() : null;
    String genus = chosenCandidate != null ? chosenCandidate.getGenus() : null;

    // Harvest the confirmed candidate's reference image onto Species (T9.A).
    String imageUrl = null;
    String imageAttribution = null;
    String imageLicense = null;
    if (chosenCandidate != null
        && chosenCandidate.getReferenceImages() != null
        && !chosenCandidate.getReferenceImages().isEmpty()) {
      var refImage = chosenCandidate.getReferenceImages().get(0);
      imageUrl = refImage.getSmallUrl() != null ? refImage.getSmallUrl() : refImage.getMediumUrl();
      imageAttribution = refImage.getAuthor();
      imageLicense = refImage.getLicense();
    }

    Species species =
        speciesService.findOrCreate(
            scientificName,
            commonName,
            toLegacyReasoningPreference(loadReasoningPreference(userId)),
            gbifId,
            powoId,
            iucnCategory,
            family,
            genus,
            imageUrl,
            imageAttribution,
            imageLicense);

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

  @Override
  @Transactional
  public IdentificationResponse retryIdentification(Long id, Long userId) {
    Identification identification = findOwnedIdentification(id, userId);

    if (identification.getIdentificationStatus() == IdentificationStageStatus.PENDING) {
      throw new PlantPalException("Identification is already in progress", 409);
    }

    ConsumptionProbe probe = consumeRateLimit(userId);
    if (!probe.isConsumed()) {
      throw new RateLimitException(
          "AI identification rate limit reached — try again later", retryAfterSeconds(probe));
    }

    if (identification.getIdentificationStatus() == IdentificationStageStatus.FAILED) {
      // Core failed — reset all stages and re-publish to Kafka so processIdentification re-runs.
      identification.setIdentificationStatus(IdentificationStageStatus.PENDING);
      identification.setAnnotationStatus(IdentificationStageStatus.PENDING);
      identification.setCandidateStatus(IdentificationStageStatus.PENDING);
      identification.setStatus(IdentificationStatus.PENDING);
      identification.setFailureReason(null);
      identification = identificationRepository.save(identification);

      VisionModelPreference preference = loadVisionPreference(userId);
      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(identification.getId())
              .userId(userId)
              .photoUrl(identification.getPhotoUrl())
              .aiModelPreference(preference.name())
              .organs(null)
              .requestedAt(Instant.now())
              .build();
      identificationDispatcher.dispatch(event);
      log.info("Retried failed identification (core): id={}", identification.getId());
    } else {
      // Core COMPLETED — re-queue only the failed enrichment stages.
      boolean annotationNeeded =
          identification.getAnnotationStatus() == IdentificationStageStatus.FAILED;
      boolean candidateNeeded =
          identification.getCandidateStatus() == IdentificationStageStatus.FAILED;

      if (annotationNeeded) identification.setAnnotationStatus(IdentificationStageStatus.PENDING);
      if (candidateNeeded) identification.setCandidateStatus(IdentificationStageStatus.PENDING);

      if (annotationNeeded || candidateNeeded) {
        identification = identificationRepository.save(identification);
        final long savedId = identification.getId();
        if (annotationNeeded) {
          CompletableFuture.runAsync(() -> enrichAnnotationForRetry(savedId), aiTaskExecutor);
        }
        if (candidateNeeded) {
          String[] pnPrefs = loadPlantNetPreferences(userId);
          final String project = pnPrefs[0];
          final String lang = pnPrefs[1];
          final String photoUrl = identification.getPhotoUrl();
          CompletableFuture.runAsync(
              () -> {
                try {
                  byte[] rawBytes = fileStorageService.loadPhotoBytes(photoUrl);
                  String mediaType = resolveMediaType(photoUrl);
                  byte[] imageBytes =
                      ImageUtil.resizeAndConvertToJpeg(rawBytes, SOURCE_IMAGE_MAX_SIDE_PX);
                  enrichWithPlantNetCandidates(savedId, imageBytes, mediaType, null, project, lang);
                } catch (Exception e) {
                  log.warn(
                      "PlantNet candidate retry load failed: identificationId={}, error={}",
                      savedId,
                      e.getMessage());
                  identificationRepository
                      .findById(savedId)
                      .ifPresent(
                          ident -> {
                            ident.setCandidateStatus(IdentificationStageStatus.FAILED);
                            identificationRepository.save(ident);
                          });
                }
              },
              aiTaskExecutor);
        }
        log.info(
            "Retried enrichment stages: id={}, annotation={}, candidates={}",
            savedId,
            annotationNeeded,
            candidateNeeded);
      }
    }

    return getIdentification(id, userId);
  }

  /**
   * Fire-and-forget annotation enrichment for the retry path. Mirrors enrichWithPlantNetCandidates
   * — loads the photo from storage, re-runs annotation, and updates annotationStatus. Never throws.
   */
  private void enrichAnnotationForRetry(Long identificationId) {
    try {
      Identification ident =
          identificationRepository
              .findById(identificationId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Identification not found for retry"));
      byte[] rawBytes = fileStorageService.loadPhotoBytes(ident.getPhotoUrl());
      String mediaType = resolveMediaType(ident.getPhotoUrl());
      byte[] imageBytes = ImageUtil.resizeAndConvertToJpeg(rawBytes, SOURCE_IMAGE_MAX_SIDE_PX);
      String annotationJson = runAnnotation(imageBytes, mediaType, ident.getUserId());
      ident.setAnnotationRegions(annotationJson);
      ident.setAnnotationStatus(IdentificationStageStatus.COMPLETED);
      ident.setAnnotationModel("gpt-4o-mini");
      identificationRepository.save(ident);
      log.info("Annotation retry completed: identificationId={}", identificationId);
    } catch (Exception e) {
      log.warn(
          "Annotation retry failed: identificationId={}, error={}",
          identificationId,
          e.getMessage());
      identificationRepository
          .findById(identificationId)
          .ifPresent(
              ident -> {
                ident.setAnnotationStatus(IdentificationStageStatus.FAILED);
                identificationRepository.save(ident);
              });
    }
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

  /**
   * Best-effort recovery for AI JSON that didn't come back clean — wrapped in a markdown code fence
   * ({@code ```json ... ```} / {@code ``` ... ```} / {@code ~~~ ... ~~~}) or preceded/followed by
   * prose. This affects every AI-JSON parse in this class, not just identification: providers
   * routed through the ai-gateway passthrough bypass each client's own stripThinkTags() fence
   * handling, so the raw string handed to us can still be fenced.
   *
   * <p>Strategy (mirrors sentinel-hub's parse.py): if the trimmed input already looks like bare
   * JSON, return it unchanged. Otherwise strip a fenced block if one is found. Otherwise fall back
   * to the pragmatic minimum — extract the substring from the first {@code '{'} to the last {@code
   * '}'}. Never throws, and never guarantees the result is valid JSON — this only rescues
   * fenced/prose-wrapped-but-otherwise-valid JSON; the caller's own try/catch still handles
   * genuinely malformed output via its existing fallback.
   */
  private static String extractJson(String raw) {
    if (raw == null) return null;
    String candidate = raw.strip();
    if (candidate.startsWith("{") || candidate.startsWith("[")) {
      return candidate;
    }

    Matcher fenceMatch = FENCED_JSON.matcher(candidate);
    if (fenceMatch.find()) {
      candidate = fenceMatch.group(1).strip();
    }
    if (candidate.startsWith("{") || candidate.startsWith("[")) {
      return candidate;
    }

    int firstBrace = candidate.indexOf('{');
    int lastBrace = candidate.lastIndexOf('}');
    if (firstBrace != -1 && lastBrace > firstBrace) {
      return candidate.substring(firstBrace, lastBrace + 1);
    }
    return candidate;
  }

  private DeepSeekPlantResult parseIdentificationResult(String raw) {
    try {
      DeepSeekPlantResult result =
          objectMapper.readValue(extractJson(raw), DeepSeekPlantResult.class);
      if (result.getCommonName() == null) result.setCommonName("Unknown Plant");
      return result;
    } catch (JsonProcessingException e) {
      log.warn("Malformed identification JSON from DeepSeek, using fallback: {}", e.getMessage());
      return new DeepSeekPlantResult(null, "Unknown Plant", "LOW", "UNKNOWN", null, null);
    }
  }

  private CureAdviceResponse parseCureAdvice(String raw, ReasoningModelPreference preference) {
    String candidate = extractJson(raw);
    try {
      CureAdviceJson parsed = objectMapper.readValue(candidate, CureAdviceJson.class);
      return CureAdviceResponse.builder()
          .advice(parsed.getAdvice())
          .actionPlan(ActionPlanValidator.normalize(parsed.getActionPlan()))
          .reasoningModelUsed(preference.name())
          .build();
    } catch (JsonProcessingException e) {
      CureAdviceJson merged = parseConcatenatedCureAdviceJson(candidate);
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
      CarePlanDto plan = objectMapper.readValue(extractJson(raw), CarePlanDto.class);
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
                            .capacity(aiCallsPerHour)
                            .refillIntervally(aiCallsPerHour, Duration.ofHours(1))
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

  /**
   * Returns [project, lang] for PlantNet calls, reading the user's stored preferences and falling
   * back to the app-level defaults when the stored value is null/blank.
   */
  private String[] loadPlantNetPreferences(Long userId) {
    return userRepository
        .findById(userId)
        .map(
            u -> {
              String project =
                  (u.getPlantnetProject() != null && !u.getPlantnetProject().isBlank())
                      ? u.getPlantnetProject()
                      : plantNetDefaultProject;
              String lang =
                  (u.getPlantnetLang() != null && !u.getPlantnetLang().isBlank())
                      ? u.getPlantnetLang()
                      : plantNetDefaultLang;
              return new String[] {project, lang};
            })
        .orElse(new String[] {plantNetDefaultProject, plantNetDefaultLang});
  }

  private String generateCureAdviceForPreference(
      ReasoningModelPreference preference, String species, String regionLabel, Long userId) {
    // Gateway routing (D022): every ReasoningModelPreference is in scope for the gateway swap
    // (Chunk 3) — unlike the vision preferences, none of them are excluded.
    if (gatewayProperties.enabled()) {
      String effectiveSpecies = species != null ? species : "Unknown plant";
      String userMessage =
          "My "
              + effectiveSpecies
              + " has the following issue: "
              + regionLabel
              + ". Provide a concise cure procedure in 3-5 numbered steps.";
      AiRequest request =
          new AiRequest()
              .prompt(userMessage)
              .modelHint(modelHintForReasoning(preference))
              .appId("plantpal")
              .userId(String.valueOf(userId))
              .putContextItem("systemPrompt", DeepSeekClient.CURE_ADVICE_SYSTEM_PROMPT)
              .putContextItem("maxTokens", GATEWAY_MAX_TOKENS);
      return gatewayClient.request(request).getResult();
    }
    return switch (preference) {
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 -> ollamaClient.generateCureAdvice(species, regionLabel);
      case ANTHROPIC_CLAUDE -> anthropicClient.generateCureAdvice(species, regionLabel);
      case GITHUB_O4_MINI -> deepSeekClient.generateCureAdviceViaO4Mini(species, regionLabel);
      case GITHUB_GPT41_MINI -> deepSeekClient.generateCureAdviceViaGpt41Mini(species, regionLabel);
      case DEEPSEEK_R1 -> deepSeekClient.generateCureAdvice(species, regionLabel);
    };
  }

  /** Configured model string per reasoning preference, for the gateway's {@code modelHint}. */
  private String modelHintForReasoning(ReasoningModelPreference preference) {
    return switch (preference) {
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 -> ollamaClient.getModel();
      case ANTHROPIC_CLAUDE -> anthropicClient.getDefaultModel();
      case GITHUB_O4_MINI -> deepSeekClient.getO4MiniModel();
      case GITHUB_GPT41_MINI -> deepSeekClient.getGpt41MiniModel();
      case DEEPSEEK_R1 -> deepSeekClient.getModel();
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
      VisionModelPreference preference,
      byte[] imageBytes,
      String mediaType,
      List<String> organs,
      String plantNetProject,
      String plantNetLang,
      String userContext,
      Long userId) {
    return switch (preference) {
      case PLANTNET -> {
        // Gateway routing (D022): organs/project/lang are attached to the gateway request's
        // context so ai-gateway's PlantNetAdapter can forward them to PlantNet, matching the
        // direct-path fidelity below.
        if (gatewayProperties.enabled()) {
          AiRequest request =
              identificationGatewayRequest(imageBytes, mediaType, "plantnet", userId, userContext)
                  .putContextItem("organs", organs != null ? organs : List.of("auto"))
                  .putContextItem("project", plantNetProject)
                  .putContextItem("lang", plantNetLang);
          PlantNetResponse pnr = parsePlantNetResponse(gatewayClient.request(request).getResult());
          yield new IdentificationOutcome(
              plantNetToRawResult(pnr), VisionModelPreference.PLANTNET.name(), pnr);
        }
        PlantNetResponse pnr =
            plantNetClient.identify(
                List.of(new ByteArrayMultipartFile(imageBytes, mediaType)),
                organs != null ? organs : List.of("auto"),
                plantNetProject,
                plantNetLang);
        yield new IdentificationOutcome(
            plantNetToRawResult(pnr), VisionModelPreference.PLANTNET.name(), pnr);
      }
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 ->
          new IdentificationOutcome(
              ollamaClient.identifyPlant(imageBytes, mediaType, userContext),
              VisionModelPreference.OLLAMA_GEMMA3.name());
      case GITHUB_GPT4O -> {
        // Gap G1 follow-up: gpt-4o identification now routes through the gateway (media support
        // shipped in OpenAiAdapter) — the direct GitHubModelsClient call retires from this hot
        // path when the gateway is enabled, same additive if/else shape as ANTHROPIC_CLAUDE below
        // (D022; direct client remains for standalone/dev, spec §2.1 seed-code pattern).
        if (gatewayProperties.enabled()) {
          yield new IdentificationOutcome(
              gatewayClient
                  .request(
                      identificationGatewayRequest(
                          imageBytes,
                          mediaType,
                          gitHubModelsClient.getIdentificationModel(),
                          userId,
                          userContext))
                  .getResult(),
              VisionModelPreference.GITHUB_GPT4O.name());
        }
        yield new IdentificationOutcome(
            gitHubModelsClient.identifyPlant(imageBytes, mediaType, userContext),
            VisionModelPreference.GITHUB_GPT4O.name());
      }
      case GITHUB_GPT41 -> {
        // Gap G1 follow-up: same gateway routing as GITHUB_GPT4O, forced onto gpt-4.1.
        if (gatewayProperties.enabled()) {
          yield new IdentificationOutcome(
              gatewayClient
                  .request(
                      identificationGatewayRequest(
                          imageBytes,
                          mediaType,
                          gitHubModelsClient.getGpt41Model(),
                          userId,
                          userContext))
                  .getResult(),
              VisionModelPreference.GITHUB_GPT41.name());
        }
        yield new IdentificationOutcome(
            gitHubModelsClient.identifyPlantWithGpt41(imageBytes, mediaType, userContext),
            VisionModelPreference.GITHUB_GPT41.name());
      }
      case ANTHROPIC_CLAUDE -> {
        if (gatewayProperties.enabled()) {
          yield new IdentificationOutcome(
              gatewayClient
                  .request(
                      identificationGatewayRequest(
                          imageBytes,
                          mediaType,
                          anthropicClient.getDefaultModel(),
                          userId,
                          userContext))
                  .getResult(),
              VisionModelPreference.ANTHROPIC_CLAUDE.name());
        }
        yield new IdentificationOutcome(
            anthropicClient.identifyPlant(imageBytes, mediaType, userContext),
            VisionModelPreference.ANTHROPIC_CLAUDE.name());
      }
    };
  }

  /**
   * Shared AiRequest builder for every gateway-routed vision preference (ANTHROPIC_CLAUDE, PLANTNET
   * identify, and — since the G1 follow-up — GITHUB_GPT4O/GITHUB_GPT41) — same user-facing prompt
   * text and system prompt every direct client uses for identification, so the gateway path never
   * restates them (Chunk 3, D022 gateway swap).
   */
  private AiRequest identificationGatewayRequest(
      byte[] imageBytes, String mediaType, String modelHint, Long userId, String userContext) {
    String basePrompt = "Identify this plant and generate a complete beginner care plan.";
    String promptText =
        (userContext != null && !userContext.isBlank())
            ? basePrompt
                + " The user wants to know: "
                + userContext
                + ". Consider this when assessing health and generating care advice"
                + " — address their specific concern directly."
            : basePrompt;
    return new AiRequest()
        .prompt(promptText)
        .modelHint(modelHint)
        .appId("plantpal")
        .userId(String.valueOf(userId))
        .putContextItem("systemPrompt", GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT)
        .putContextItem("maxTokens", GATEWAY_IDENTIFICATION_MAX_TOKENS)
        .addMediaItem(new AiRequestMediaInner().data(imageBytes).mimeType(mediaType));
  }

  /**
   * Gap G1 follow-up: routes the always-on gpt-4o-mini annotation call (visual region polygons)
   * through the gateway when enabled — same additive if/else shape used throughout this class for
   * D022's gateway swap. {@code userId} may be {@code null} for system-initiated retries with no
   * resolvable user (falls back to "system", matching the convention used for species enrichment).
   */
  private String runAnnotation(byte[] imageBytes, String mediaType, Long userId) {
    if (gatewayProperties.enabled()) {
      AiRequest request =
          new AiRequest()
              .prompt("Identify and locate all plant regions in this image.")
              .modelHint(gitHubModelsClient.getAnnotationModel())
              .appId("plantpal")
              .userId(userId != null ? String.valueOf(userId) : "system")
              .putContextItem("systemPrompt", GitHubModelsClient.ANNOTATION_SYSTEM_PROMPT)
              .putContextItem("maxTokens", GATEWAY_MAX_TOKENS)
              .addMediaItem(new AiRequestMediaInner().data(imageBytes).mimeType(mediaType));
      return gatewayClient.request(request).getResult();
    }
    return visionAnnotationClient.analyzeRegions(imageBytes, mediaType);
  }

  private PlantNetResponse parsePlantNetResponse(String rawJson) {
    try {
      return objectMapper.readValue(rawJson, PlantNetResponse.class);
    } catch (JsonProcessingException e) {
      throw new PlantPalException("Failed to parse PlantNet gateway response", 502, e);
    }
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

  private static String classifyFailureReason(Exception e) {
    if (e instanceof RateLimitException) return "RATE_LIMITED";
    if (e instanceof JsonProcessingException) return "PARSE_ERROR";
    // A 402 from the ai-gateway means the user/app hit its AI ceiling ("daily AI limit reached").
    // Tagged distinctly from a generic PROVIDER_ERROR (503 etc.) so the frontend poller can surface
    // an explicit "AI limit reached" block state instead of a generic "something broke" failure
    // (platform D023 — a block is NEVER a spinner and never a generic error).
    if (e instanceof PlantPalException ppe && ppe.getErrorCode() == 402) return "AI_LIMIT_REACHED";
    if (e instanceof PlantPalException) return "PROVIDER_ERROR";
    return "OTHER";
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
    // T-DEPLOY.5: this topic has no in-repo consumer (cross-repo/platform notification only), so
    // in in-process mode it's simply skipped rather than routed through IdentificationDispatcher —
    // unlike the identification-requested dispatch, there's no local processing waiting on it.
    if (kafkaTransportProperties.isKafkaEnabled()) {
      kafkaTemplate.send(KafkaTopicConfig.IDENTIFICATION_COMPLETED_TOPIC, event);
    } else {
      log.debug(
          "Skipping identification.completed Kafka publish (transport=in-process): id={}",
          identificationId);
    }
    // Also published as a Spring application event (in addition to the Kafka message above) so
    // in-process listeners — e.g. com.plantpal.statefeed.StateFeedEmitter — can react without a
    // Kafka consumer or a new cross-package injection.
    eventPublisher.publishEvent(event);
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
