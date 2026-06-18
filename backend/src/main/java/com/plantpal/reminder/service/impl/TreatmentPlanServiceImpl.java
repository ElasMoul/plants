package com.plantpal.reminder.service.impl;

import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.dto.TreatmentStepDto;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.dto.TreatmentPlanResponse;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.entity.TreatmentPlan;
import com.plantpal.reminder.entity.TreatmentPlanStatus;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.repository.TreatmentPlanRepository;
import com.plantpal.reminder.service.TreatmentPlanService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TreatmentPlanServiceImpl implements TreatmentPlanService {

  private static final Logger log = LoggerFactory.getLogger(TreatmentPlanServiceImpl.class);

  private static final String TREATMENT_TYPE = "TREATMENT";

  private final TreatmentPlanRepository treatmentPlanRepository;
  private final ReminderRepository reminderRepository;
  private final PlantRepository plantRepository;

  public TreatmentPlanServiceImpl(
      TreatmentPlanRepository treatmentPlanRepository,
      ReminderRepository reminderRepository,
      PlantRepository plantRepository) {
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.reminderRepository = reminderRepository;
    this.plantRepository = plantRepository;
  }

  @Override
  @Transactional
  public TreatmentPlanResponse createFromActionPlan(
      Long plantId,
      Long userId,
      String title,
      String sourceCareCardType,
      ActionPlanDto actionPlan) {
    if (actionPlan == null || !TREATMENT_TYPE.equalsIgnoreCase(actionPlan.getType())) {
      throw new ValidationException("Action plan must be of type TREATMENT");
    }
    if (actionPlan.getSteps() == null || actionPlan.getSteps().isEmpty()) {
      throw new ValidationException("Treatment action plan must have at least one step");
    }

    Plant plant =
        plantRepository
            .findByIdAndUserId(plantId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));
    CareType careType = parseCareType(sourceCareCardType);

    TreatmentPlan plan =
        TreatmentPlan.builder()
            .plantId(plant.getId())
            .userId(userId)
            .title(title)
            .sourceCareCardType(sourceCareCardType)
            .diagramFormat(
                actionPlan.getDiagram() != null ? actionPlan.getDiagram().getFormat() : null)
            .diagramContent(
                actionPlan.getDiagram() != null ? actionPlan.getDiagram().getContent() : null)
            .status(TreatmentPlanStatus.ACTIVE)
            .build();
    plan = treatmentPlanRepository.save(plan);

    Instant now = Instant.now();
    for (TreatmentStepDto step : actionPlan.getSteps()) {
      reminderRepository.save(
          Reminder.builder()
              .plantId(plant.getId())
              .userId(userId)
              .careType(careType)
              .frequencyDays(0)
              .nextDueAt(now.plus(step.getDueOffsetDays(), ChronoUnit.DAYS))
              .enabled(true)
              .recurring(false)
              .treatmentPlanId(plan.getId())
              .treatmentPlanTitle(title)
              .stepOrder(step.getOrder())
              .build());
    }
    log.info(
        "Treatment plan created: id={}, plantId={}, steps={}",
        plan.getId(),
        plant.getId(),
        actionPlan.getSteps().size());

    return toResponse(plan, plant);
  }

  @Override
  @Transactional(readOnly = true)
  public TreatmentPlanResponse getTreatmentPlan(Long id, Long userId) {
    TreatmentPlan plan =
        treatmentPlanRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
    Plant plant = plantRepository.findById(plan.getPlantId()).orElse(null);
    return toResponse(plan, plant);
  }

  private CareType parseCareType(String sourceCareCardType) {
    if (sourceCareCardType == null || sourceCareCardType.isBlank()) {
      throw new ValidationException("sourceCareCardType is required to create treatment reminders");
    }
    try {
      return CareType.valueOf(sourceCareCardType.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown care card type: " + sourceCareCardType);
    }
  }

  private TreatmentPlanResponse toResponse(TreatmentPlan plan, Plant plant) {
    List<ReminderResponse> steps =
        reminderRepository.findByTreatmentPlanIdOrderByStepOrder(plan.getId()).stream()
            .map(r -> toReminderResponse(r, plant))
            .toList();
    return TreatmentPlanResponse.builder()
        .id(plan.getId())
        .plantId(plan.getPlantId())
        .title(plan.getTitle())
        .diagramFormat(plan.getDiagramFormat())
        .diagramContent(plan.getDiagramContent())
        .status(plan.getStatus().name())
        .steps(steps)
        .build();
  }

  private ReminderResponse toReminderResponse(Reminder reminder, Plant plant) {
    return ReminderResponse.builder()
        .id(reminder.getId())
        .plantId(reminder.getPlantId())
        .plantNickname(plant != null ? plant.getNickname() : null)
        .plantPhotoUrl(plant != null ? plant.getPhotoUrl() : null)
        .careType(reminder.getCareType())
        .frequencyDays(reminder.getFrequencyDays())
        .nextDueAt(reminder.getNextDueAt())
        .enabled(reminder.isEnabled())
        .recurring(reminder.isRecurring())
        .treatmentPlanId(reminder.getTreatmentPlanId())
        .treatmentPlanTitle(reminder.getTreatmentPlanTitle())
        .stepOrder(reminder.getStepOrder())
        .build();
  }
}
