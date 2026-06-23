package com.plantpal.treatment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.util.ActionPlanValidator;
import com.plantpal.identification.util.LenientJsonParser;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.service.TreatmentPlanService;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.treatment.dto.CreateTreatmentRequest;
import com.plantpal.treatment.dto.TreatmentResponse;
import com.plantpal.treatment.entity.Treatment;
import com.plantpal.treatment.entity.TreatmentStatus;
import com.plantpal.treatment.repository.TreatmentRepository;
import com.plantpal.treatment.service.TreatmentService;
import com.plantpal.user.entity.ReasoningModelPreference;
import com.plantpal.user.repository.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TreatmentServiceImpl implements TreatmentService {

  private static final Logger log = LoggerFactory.getLogger(TreatmentServiceImpl.class);

  private static final List<TreatmentStatus> ACTIVE_STATUSES =
      List.of(TreatmentStatus.DRAFT, TreatmentStatus.IN_PROGRESS);
  private static final String DISEASE_CARE_CARD_TYPE = "PEST";
  private static final int TREATMENT_AI_RATE_LIMIT = 100000;
  // Disease description is fire-and-forget (no HTTP caller waiting), so a single bounded retry
  // after the upstream-suggested wait is safe — caps the sleep in case of an implausible value.
  private static final long MAX_DISEASE_DESCRIPTION_RETRY_WAIT_SECONDS = 65;

  private final TreatmentRepository treatmentRepository;
  private final PlantRepository plantRepository;
  private final TreatmentPlanService treatmentPlanService;
  private final DeepSeekClient deepSeekClient;
  private final OllamaClient ollamaClient;
  private final AnthropicClient anthropicClient;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;
  private final Executor aiTaskExecutor;

  private final Map<Long, Bucket> aiBuckets = new ConcurrentHashMap<>();

  public TreatmentServiceImpl(
      TreatmentRepository treatmentRepository,
      PlantRepository plantRepository,
      TreatmentPlanService treatmentPlanService,
      DeepSeekClient deepSeekClient,
      OllamaClient ollamaClient,
      AnthropicClient anthropicClient,
      UserRepository userRepository,
      ObjectMapper objectMapper,
      @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
    this.treatmentRepository = treatmentRepository;
    this.plantRepository = plantRepository;
    this.treatmentPlanService = treatmentPlanService;
    this.deepSeekClient = deepSeekClient;
    this.ollamaClient = ollamaClient;
    this.anthropicClient = anthropicClient;
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
    this.aiTaskExecutor = aiTaskExecutor;
  }

  @Override
  @Transactional
  public TreatmentResponse createTreatment(CreateTreatmentRequest request, Long userId) {
    Plant plant =
        plantRepository
            .findByIdAndUserId(request.getPlantId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));

    boolean alreadyActive =
        !treatmentRepository
            .findByPlantIdAndDiseaseNameAndStatusIn(
                plant.getId(), request.getDiseaseName(), ACTIVE_STATUSES)
            .isEmpty();
    if (alreadyActive) {
      throw new ValidationException(
          "An active treatment already exists for this plant and disease");
    }

    Treatment treatment =
        treatmentRepository.save(
            Treatment.builder()
                .plantId(plant.getId())
                .userId(userId)
                .identificationId(request.getIdentificationId())
                .diseaseName(request.getDiseaseName())
                .status(TreatmentStatus.DRAFT)
                .build());
    log.info(
        "Treatment created: id={}, plantId={}, diseaseName={}",
        treatment.getId(),
        plant.getId(),
        request.getDiseaseName());

    fireDiseaseDescriptionGeneration(treatment.getId(), plant, request.getDiseaseName(), userId);

    return toResponse(treatment);
  }

  @Override
  @Async("aiTaskExecutor")
  @Transactional
  public CompletableFuture<TreatmentResponse> craftPlan(Long id, Long userId) {
    Treatment treatment = findOwnedTreatment(id, userId);
    if (treatment.getStatus() != TreatmentStatus.DRAFT) {
      throw new ValidationException("Treatment plan can only be crafted from DRAFT status");
    }
    ConsumptionProbe rateLimitProbe = consumeAiRateLimit(userId);
    if (!rateLimitProbe.isConsumed()) {
      throw new RateLimitException(
          "Treatment AI rate limit reached — try again later", retryAfterSeconds(rateLimitProbe));
    }
    Plant plant =
        plantRepository
            .findByIdAndUserId(treatment.getPlantId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));

    String species = plant.getSpecies() != null ? plant.getSpecies() : plant.getCommonName();
    ReasoningModelPreference preference = loadReasoningPreference(userId);
    String raw = generateCureAdvice(preference, species, treatment.getDiseaseName());
    ActionPlanDto actionPlan = ActionPlanValidator.normalize(parseActionPlan(raw));

    var plan =
        treatmentPlanService.createFromActionPlan(
            plant.getId(), userId, treatment.getDiseaseName(), DISEASE_CARE_CARD_TYPE, actionPlan);

    treatment.setTreatmentPlanId(plan.getId());
    treatment.setTreatmentPlanModel(preference.name());
    treatment.setStatus(TreatmentStatus.IN_PROGRESS);
    treatment.setStartedAt(Instant.now());
    treatment = treatmentRepository.save(treatment);
    log.info(
        "Treatment plan crafted: treatmentId={}, treatmentPlanId={}",
        treatment.getId(),
        plan.getId());

    plant.setActiveTreatmentId(treatment.getId());
    plantRepository.save(plant);

    return CompletableFuture.completedFuture(toResponse(treatment));
  }

  @Override
  @Transactional(readOnly = true)
  public TreatmentResponse getTreatment(Long id, Long userId) {
    return toResponse(findOwnedTreatment(id, userId));
  }

  @Override
  @Transactional(readOnly = true)
  public TreatmentResponse getActiveTreatmentForPlant(Long plantId, Long userId) {
    return treatmentRepository
        .findFirstByPlantIdAndUserIdAndStatusInOrderByCreatedAtDesc(
            plantId, userId, ACTIVE_STATUSES)
        .map(this::toResponse)
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TreatmentResponse> getActiveTreatmentsForPlant(Long plantId, Long userId) {
    return treatmentRepository
        .findByPlantIdAndUserIdAndStatusInOrderByCreatedAtDesc(plantId, userId, ACTIVE_STATUSES)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public TreatmentResponse completeTreatment(Long id, Long userId) {
    Treatment treatment = findOwnedTreatment(id, userId);
    if (treatment.getStatus() != TreatmentStatus.IN_PROGRESS) {
      throw new ValidationException("Only an IN_PROGRESS treatment can be completed");
    }
    Optional<Plant> plant = plantRepository.findByIdAndUserId(treatment.getPlantId(), userId);
    treatment = markCompleted(treatment, plant);
    log.info("Treatment completed: id={}, userId={}", id, userId);

    return toResponse(treatment);
  }

  @Override
  @Transactional
  public void dismissActiveTreatmentForDisease(Long plantId, String diseaseName) {
    List<Treatment> active =
        treatmentRepository.findByPlantIdAndDiseaseNameAndStatusIn(
            plantId, diseaseName, ACTIVE_STATUSES);
    for (Treatment treatment : active) {
      treatment.setStatus(TreatmentStatus.DISMISSED);
      treatment = treatmentRepository.save(treatment);
      Long dismissedId = treatment.getId();
      plantRepository
          .findById(plantId)
          .filter(p -> dismissedId.equals(p.getActiveTreatmentId()))
          .ifPresent(
              p -> {
                p.setActiveTreatmentId(null);
                plantRepository.save(p);
              });
      log.info(
          "Treatment dismissed via duplicate care card cleanup: treatmentId={}, plantId={},"
              + " diseaseName={}",
          treatment.getId(),
          plantId,
          diseaseName);
    }
  }

  @Override
  @Transactional
  public void syncFromTreatmentPlanCompletion(Long treatmentPlanId) {
    for (Treatment treatment : treatmentRepository.findByTreatmentPlanId(treatmentPlanId)) {
      if (treatment.getStatus() != TreatmentStatus.IN_PROGRESS) {
        continue;
      }
      Optional<Plant> plant = plantRepository.findById(treatment.getPlantId());
      markCompleted(treatment, plant);
      log.info(
          "Treatment auto-completed via TreatmentPlan sync: treatmentId={}, treatmentPlanId={}",
          treatment.getId(),
          treatmentPlanId);
    }
  }

  /** Shared by {@link #completeTreatment} and {@link #syncFromTreatmentPlanCompletion}. */
  private Treatment markCompleted(Treatment treatment, Optional<Plant> plant) {
    treatment.setStatus(TreatmentStatus.COMPLETED);
    treatment.setCompletedAt(Instant.now());
    treatment = treatmentRepository.save(treatment);

    Long completedTreatmentId = treatment.getId();
    plant
        .filter(p -> completedTreatmentId.equals(p.getActiveTreatmentId()))
        .ifPresent(
            p -> {
              p.setActiveTreatmentId(null);
              plantRepository.save(p);
            });

    return treatment;
  }

  private Treatment findOwnedTreatment(Long id, Long userId) {
    return treatmentRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Treatment not found"));
  }

  private void fireDiseaseDescriptionGeneration(
      Long treatmentId, Plant plant, String diseaseName, Long userId) {
    if (!consumeAiRateLimit(userId).isConsumed()) {
      log.warn(
          "Treatment AI rate limit reached, skipping disease description: treatmentId={},"
              + " userId={}",
          treatmentId,
          userId);
      return;
    }
    String species = plant.getSpecies() != null ? plant.getSpecies() : plant.getCommonName();
    ReasoningModelPreference preference = loadReasoningPreference(userId);
    CompletableFuture.runAsync(
        () -> generateAndSaveDiseaseDescription(treatmentId, preference, species, diseaseName),
        aiTaskExecutor);
  }

  /**
   * Fire-and-forget, so a single bounded retry after an upstream 429's suggested wait is safe here
   * (unlike {@link #craftPlan}, no HTTP caller is blocked on this) — this is what actually fixes
   * "description never generated" rather than just logging the failure once and giving up.
   */
  private void generateAndSaveDiseaseDescription(
      Long treatmentId, ReasoningModelPreference preference, String species, String diseaseName) {
    try {
      saveDiseaseDescription(
          treatmentId, generateDiseaseDescription(preference, species, diseaseName), preference);
    } catch (RateLimitException e) {
      long waitSeconds =
          Math.min(
              e.getRetryAfterSeconds() != null ? e.getRetryAfterSeconds() : 0,
              MAX_DISEASE_DESCRIPTION_RETRY_WAIT_SECONDS);
      log.warn(
          "Disease description rate-limited, retrying once in {}s: treatmentId={}",
          waitSeconds,
          treatmentId);
      try {
        Thread.sleep(Duration.ofSeconds(waitSeconds));
        saveDiseaseDescription(
            treatmentId, generateDiseaseDescription(preference, species, diseaseName), preference);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      } catch (PlantPalException retryFailure) {
        log.warn(
            "Disease description retry also failed, leaving null: treatmentId={}, error={}",
            treatmentId,
            retryFailure.getMessage());
      }
    } catch (PlantPalException e) {
      log.warn(
          "Disease description generation failed, leaving null: treatmentId={}, error={}",
          treatmentId,
          e.getMessage());
    }
  }

  private void saveDiseaseDescription(
      Long treatmentId, String description, ReasoningModelPreference preference) {
    treatmentRepository
        .findById(treatmentId)
        .ifPresent(
            t -> {
              t.setDiseaseDescription(description);
              t.setDiseaseDescriptionModel(preference.name());
              treatmentRepository.save(t);
              log.info("Disease description saved: treatmentId={}", treatmentId);
            });
  }

  private ReasoningModelPreference loadReasoningPreference(Long userId) {
    return userRepository
        .findById(userId)
        .map(user -> user.getReasoningModelPreference())
        .orElse(ReasoningModelPreference.DEEPSEEK_R1);
  }

  private String generateCureAdvice(
      ReasoningModelPreference preference, String species, String diseaseName) {
    return switch (preference) {
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 -> ollamaClient.generateCureAdvice(species, diseaseName);
      case ANTHROPIC_CLAUDE -> anthropicClient.generateCureAdvice(species, diseaseName);
      case GITHUB_O4_MINI -> deepSeekClient.generateCureAdviceViaO4Mini(species, diseaseName);
      case GITHUB_GPT41_MINI -> deepSeekClient.generateCureAdviceViaGpt41Mini(species, diseaseName);
      case DEEPSEEK_R1 -> deepSeekClient.generateCureAdvice(species, diseaseName);
    };
  }

  private String generateDiseaseDescription(
      ReasoningModelPreference preference, String species, String diseaseName) {
    return switch (preference) {
      case OLLAMA_LLAVA, OLLAMA_GEMMA3 ->
          ollamaClient.generateDiseaseDescription(species, diseaseName);
      case ANTHROPIC_CLAUDE -> anthropicClient.generateDiseaseDescription(species, diseaseName);
      case GITHUB_O4_MINI ->
          deepSeekClient.generateDiseaseDescriptionViaO4Mini(species, diseaseName);
      case GITHUB_GPT41_MINI ->
          deepSeekClient.generateDiseaseDescriptionViaGpt41Mini(species, diseaseName);
      case DEEPSEEK_R1 -> deepSeekClient.generateDiseaseDescription(species, diseaseName);
    };
  }

  private ActionPlanDto parseActionPlan(String raw) {
    try {
      CureAdviceJson parsed = objectMapper.readValue(raw, CureAdviceJson.class);
      return parsed.getActionPlan();
    } catch (JsonProcessingException e) {
      // Some local models (Ollama/llava-phi3) emit two sibling JSON objects for this prompt
      // instead of one combined object — see LenientJsonParser.
      JsonNode merged = LenientJsonParser.mergeConcatenatedObjects(objectMapper, raw);
      if (merged != null) {
        try {
          return objectMapper.treeToValue(merged, CureAdviceJson.class).getActionPlan();
        } catch (JsonProcessingException ignored) {
          // fall through to the warning + null below
        }
      }
      log.warn("Malformed treatment action plan JSON: {}", e.getMessage());
      return null;
    }
  }

  private ConsumptionProbe consumeAiRateLimit(Long userId) {
    Bucket bucket =
        aiBuckets.computeIfAbsent(
            userId,
            id ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(TREATMENT_AI_RATE_LIMIT)
                            .refillIntervally(TREATMENT_AI_RATE_LIMIT, Duration.ofHours(1))
                            .build())
                    .build());
    return bucket.tryConsumeAndReturnRemaining(1);
  }

  private static long retryAfterSeconds(ConsumptionProbe probe) {
    return Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();
  }

  private TreatmentResponse toResponse(Treatment treatment) {
    return TreatmentResponse.builder()
        .id(treatment.getId())
        .plantId(treatment.getPlantId())
        .identificationId(treatment.getIdentificationId())
        .diseaseName(treatment.getDiseaseName())
        .diseaseDescription(treatment.getDiseaseDescription())
        .diseaseDescriptionModel(treatment.getDiseaseDescriptionModel())
        .treatmentPlanModel(treatment.getTreatmentPlanModel())
        .status(treatment.getStatus())
        .treatmentPlanId(treatment.getTreatmentPlanId())
        .startedAt(treatment.getStartedAt())
        .completedAt(treatment.getCompletedAt())
        .build();
  }

  /**
   * Wire shape returned by {@link DeepSeekClient#generateCureAdvice} — same as {@code
   * IdentificationServiceImpl}'s private holder; only {@code actionPlan} is used here, {@code
   * advice} is discarded since {@link #createTreatment} already generates a dedicated
   * diseaseDescription via {@link DeepSeekClient#generateDiseaseDescription}.
   */
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
}
