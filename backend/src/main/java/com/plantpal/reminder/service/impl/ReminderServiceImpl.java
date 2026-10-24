package com.plantpal.reminder.service.impl;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.CareIntervalCalculator;
import com.plantpal.reminder.CareIntervalCalculator.CareInterval;
import com.plantpal.reminder.dto.CreateReminderRequest;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.Hemisphere;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.entity.TreatmentPlanStatus;
import com.plantpal.reminder.event.TreatmentPlanCompletedEvent;
import com.plantpal.reminder.mapper.ReminderMapper;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.repository.TreatmentPlanRepository;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.user.entity.User;
import com.plantpal.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderServiceImpl implements ReminderService {

  private static final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);

  // Personal-garden app — bounded list rather than unpaged, generous cap for active reminders.
  private static final int MAX_REMINDERS = 200;

  private final ReminderRepository reminderRepository;
  private final PlantRepository plantRepository;
  private final CareLogRepository careLogRepository;
  private final TreatmentPlanRepository treatmentPlanRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final UserRepository userRepository;
  private final Clock clock;

  public ReminderServiceImpl(
      ReminderRepository reminderRepository,
      PlantRepository plantRepository,
      CareLogRepository careLogRepository,
      TreatmentPlanRepository treatmentPlanRepository,
      ApplicationEventPublisher eventPublisher,
      UserRepository userRepository,
      Clock clock) {
    this.reminderRepository = reminderRepository;
    this.plantRepository = plantRepository;
    this.careLogRepository = careLogRepository;
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.eventPublisher = eventPublisher;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ReminderResponse createReminder(CreateReminderRequest request, Long userId) {
    Plant plant =
        plantRepository
            .findByIdAndUserId(request.getPlantId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));

    Reminder reminder =
        Reminder.builder()
            .plantId(plant.getId())
            .userId(userId)
            .careType(request.getCareType())
            .frequencyDays(request.getFrequencyDays())
            .nextDueAt(request.getFirstDueAt())
            .enabled(true)
            .build();
    reminder = reminderRepository.save(reminder);
    log.info(
        "Reminder created: id={}, plantId={}, userId={}", reminder.getId(), plant.getId(), userId);

    return toResponse(reminder, plant, hemisphereOf(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReminderResponse> getUserReminders(Long userId) {
    List<Reminder> reminders =
        reminderRepository
            .findByUserIdAndEnabledTrue(
                userId, PageRequest.of(0, MAX_REMINDERS, Sort.by("nextDueAt").ascending()))
            .getContent();

    List<Long> plantIds = reminders.stream().map(Reminder::getPlantId).distinct().toList();
    Map<Long, Plant> plantsById =
        plantRepository.findAllById(plantIds).stream()
            .collect(Collectors.toMap(Plant::getId, p -> p));

    Hemisphere hemisphere = hemisphereOf(userId);
    return reminders.stream()
        .map(r -> toResponse(r, plantsById.get(r.getPlantId()), hemisphere))
        .toList();
  }

  @Override
  @Transactional
  public ReminderResponse completeReminder(Long id, Long userId) {
    Reminder reminder =
        reminderRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Reminder not found"));

    Instant performedAt = Instant.now();
    careLogRepository.save(
        CareLog.builder()
            .plantId(reminder.getPlantId())
            .userId(userId)
            .careType(reminder.getCareType())
            .performedAt(performedAt)
            .build());

    applyCompletionToReminder(reminder, performedAt);
    log.info("Reminder completed: id={}, userId={}", id, userId);

    Plant plant = plantRepository.findById(reminder.getPlantId()).orElse(null);
    return toResponse(reminder, plant, hemisphereOf(userId));
  }

  @Override
  @Transactional
  public ReminderResponse updateInterval(Long id, Integer frequencyDays, Long userId) {
    Reminder reminder =
        reminderRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Reminder not found"));
    reminder.setOverrideFrequencyDays(frequencyDays);
    reminder = reminderRepository.save(reminder);
    log.info("Reminder interval override set: id={}, days={}", id, frequencyDays);

    Plant plant = plantRepository.findById(reminder.getPlantId()).orElse(null);
    return toResponse(reminder, plant, hemisphereOf(userId));
  }

  @Override
  @Transactional
  public void deleteReminder(Long id, Long userId) {
    Reminder reminder =
        reminderRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Reminder not found"));
    reminder.setEnabled(false);
    reminderRepository.save(reminder);
    log.info("Reminder disabled: id={}, userId={}", id, userId);
  }

  @Override
  public Instant calculateNextDueAt(Instant lastDone, int frequencyDays) {
    return lastDone.plus(frequencyDays, ChronoUnit.DAYS);
  }

  @Override
  @Transactional
  public void applyCompletionToReminder(Reminder reminder, Instant performedAt) {
    if (reminder.isRecurring()) {
      Plant plant = plantRepository.findById(reminder.getPlantId()).orElse(null);
      CareInterval interval = intervalFor(reminder, plant, hemisphereOf(reminder.getUserId()));
      reminder.setNextDueAt(calculateNextDueAt(performedAt, interval.days()));
      reminderRepository.save(reminder);
      return;
    }

    if (!reminder.isEnabled()) {
      throw new ValidationException("This reminder has already been completed");
    }

    reminder.setEnabled(false);
    reminderRepository.save(reminder);

    Long treatmentPlanId = reminder.getTreatmentPlanId();
    if (treatmentPlanId == null) {
      return;
    }
    List<Reminder> remainingSteps =
        reminderRepository.findByTreatmentPlanIdAndEnabledTrue(treatmentPlanId);
    if (remainingSteps.isEmpty()) {
      treatmentPlanRepository
          .findById(treatmentPlanId)
          .ifPresent(
              plan -> {
                plan.setStatus(TreatmentPlanStatus.COMPLETED);
                treatmentPlanRepository.save(plan);
                log.info("Treatment plan completed: id={}", treatmentPlanId);
                eventPublisher.publishEvent(new TreatmentPlanCompletedEvent(treatmentPlanId));
              });
    }
  }

  private ReminderResponse toResponse(Reminder reminder, Plant plant, Hemisphere hemisphere) {
    return ReminderMapper.toResponse(reminder, plant, intervalFor(reminder, plant, hemisphere));
  }

  private CareInterval intervalFor(Reminder reminder, Plant plant, Hemisphere hemisphere) {
    if (!reminder.isRecurring()) {
      return new CareInterval(reminder.getFrequencyDays(), null, null);
    }
    return CareIntervalCalculator.calculate(
        reminder.getCareType(),
        reminder.getFrequencyDays(),
        LocalDate.now(clock),
        hemisphere,
        plant != null ? plant.getPlacement() : null,
        reminder.getOverrideFrequencyDays());
  }

  // Null when the user has no region set; the calculator then defaults to northern and says so.
  private Hemisphere hemisphereOf(Long userId) {
    return userRepository
        .findById(userId)
        .map(User::getRegionCountry)
        .map(Hemisphere::fromCountry)
        .orElse(null);
  }
}
