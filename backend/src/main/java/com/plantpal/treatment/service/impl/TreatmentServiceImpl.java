package com.plantpal.treatment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.util.ActionPlanValidator;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.service.TreatmentPlanService;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.treatment.dto.CreateTreatmentRequest;
import com.plantpal.treatment.dto.TreatmentResponse;
import com.plantpal.treatment.entity.Treatment;
import com.plantpal.treatment.entity.TreatmentStatus;
import com.plantpal.treatment.repository.TreatmentRepository;
import com.plantpal.treatment.service.TreatmentService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
  private static final int TREATMENT_AI_RATE_LIMIT = 10;

  private final TreatmentRepository treatmentRepository;
  private final PlantRepository plantRepository;
  private final TreatmentPlanService treatmentPlanService;
  private final DeepSeekClient deepSeekClient;
  private final ObjectMapper objectMapper;
  private final Executor aiTaskExecutor;

  private final Map<Long, Bucket> aiBuckets = new ConcurrentHashMap<>();

  public TreatmentServiceImpl(
      TreatmentRepository treatmentRepository,
      PlantRepository plantRepository,
      TreatmentPlanService treatmentPlanService,
      DeepSeekClient deepSeekClient,
      ObjectMapper objectMapper,
      @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
    this.treatmentRepository = treatmentRepository;
    this.plantRepository = plantRepository;
    this.treatmentPlanService = treatmentPlanService;
    this.deepSeekClient = deepSeekClient;
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
    Plant plant =
        plantRepository
            .findByIdAndUserId(treatment.getPlantId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));

    String species = plant.getSpecies() != null ? plant.getSpecies() : plant.getCommonName();
    String raw = deepSeekClient.generateCureAdvice(species, treatment.getDiseaseName());
    ActionPlanDto actionPlan = ActionPlanValidator.normalize(parseActionPlan(raw));

    var plan =
        treatmentPlanService.createFromActionPlan(
            plant.getId(), userId, treatment.getDiseaseName(), DISEASE_CARE_CARD_TYPE, actionPlan);

    treatment.setTreatmentPlanId(plan.getId());
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
  @Transactional
  public TreatmentResponse completeTreatment(Long id, Long userId) {
    Treatment treatment = findOwnedTreatment(id, userId);
    if (treatment.getStatus() != TreatmentStatus.IN_PROGRESS) {
      throw new ValidationException("Only an IN_PROGRESS treatment can be completed");
    }
    treatment.setStatus(TreatmentStatus.COMPLETED);
    treatment.setCompletedAt(Instant.now());
    treatment = treatmentRepository.save(treatment);
    log.info("Treatment completed: id={}, userId={}", id, userId);

    Long completedTreatmentId = treatment.getId();
    plantRepository
        .findByIdAndUserId(treatment.getPlantId(), userId)
        .filter(plant -> completedTreatmentId.equals(plant.getActiveTreatmentId()))
        .ifPresent(
            plant -> {
              plant.setActiveTreatmentId(null);
              plantRepository.save(plant);
            });

    return toResponse(treatment);
  }

  private Treatment findOwnedTreatment(Long id, Long userId) {
    return treatmentRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Treatment not found"));
  }

  private void fireDiseaseDescriptionGeneration(
      Long treatmentId, Plant plant, String diseaseName, Long userId) {
    if (!consumeAiRateLimit(userId)) {
      log.warn(
          "Treatment AI rate limit reached, skipping disease description: treatmentId={},"
              + " userId={}",
          treatmentId,
          userId);
      return;
    }
    String species = plant.getSpecies() != null ? plant.getSpecies() : plant.getCommonName();
    CompletableFuture.runAsync(
        () -> generateAndSaveDiseaseDescription(treatmentId, species, diseaseName), aiTaskExecutor);
  }

  private void generateAndSaveDiseaseDescription(
      Long treatmentId, String species, String diseaseName) {
    try {
      String description = deepSeekClient.generateDiseaseDescription(species, diseaseName);
      treatmentRepository
          .findById(treatmentId)
          .ifPresent(
              t -> {
                t.setDiseaseDescription(description);
                treatmentRepository.save(t);
                log.info("Disease description saved: treatmentId={}", treatmentId);
              });
    } catch (PlantPalException e) {
      log.warn(
          "Disease description generation failed, leaving null: treatmentId={}, error={}",
          treatmentId,
          e.getMessage());
    }
  }

  private ActionPlanDto parseActionPlan(String raw) {
    try {
      CureAdviceJson parsed = objectMapper.readValue(raw, CureAdviceJson.class);
      return parsed.getActionPlan();
    } catch (JsonProcessingException e) {
      log.warn("Malformed treatment action plan JSON: {}", e.getMessage());
      return null;
    }
  }

  private boolean consumeAiRateLimit(Long userId) {
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
    return bucket.tryConsume(1);
  }

  private TreatmentResponse toResponse(Treatment treatment) {
    return TreatmentResponse.builder()
        .id(treatment.getId())
        .plantId(treatment.getPlantId())
        .identificationId(treatment.getIdentificationId())
        .diseaseName(treatment.getDiseaseName())
        .diseaseDescription(treatment.getDiseaseDescription())
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
