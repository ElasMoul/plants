package com.plantpal.reminder.service.impl;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.dto.CreateReminderRequest;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  public ReminderServiceImpl(
      ReminderRepository reminderRepository,
      PlantRepository plantRepository,
      CareLogRepository careLogRepository) {
    this.reminderRepository = reminderRepository;
    this.plantRepository = plantRepository;
    this.careLogRepository = careLogRepository;
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

    return toResponse(reminder, plant);
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

    return reminders.stream().map(r -> toResponse(r, plantsById.get(r.getPlantId()))).toList();
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

    reminder.setNextDueAt(calculateNextDueAt(performedAt, reminder.getFrequencyDays()));
    reminder = reminderRepository.save(reminder);
    log.info("Reminder completed: id={}, userId={}", id, userId);

    Plant plant = plantRepository.findById(reminder.getPlantId()).orElse(null);
    return toResponse(reminder, plant);
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

  private ReminderResponse toResponse(Reminder reminder, Plant plant) {
    return ReminderResponse.builder()
        .id(reminder.getId())
        .plantId(reminder.getPlantId())
        .plantNickname(plant != null ? plant.getNickname() : null)
        .plantPhotoUrl(plant != null ? plant.getPhotoUrl() : null)
        .careType(reminder.getCareType())
        .frequencyDays(reminder.getFrequencyDays())
        .nextDueAt(reminder.getNextDueAt())
        .enabled(reminder.isEnabled())
        .build();
  }
}
