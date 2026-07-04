package com.plantpal.reminder.service.impl;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.dto.CareLogResponse;
import com.plantpal.reminder.dto.MarkCareDoneRequest;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.CareLogService;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareLogServiceImpl implements CareLogService {

  private static final Logger log = LoggerFactory.getLogger(CareLogServiceImpl.class);

  private final CareLogRepository careLogRepository;
  private final ReminderRepository reminderRepository;
  private final PlantRepository plantRepository;
  private final ReminderService reminderService;

  public CareLogServiceImpl(
      CareLogRepository careLogRepository,
      ReminderRepository reminderRepository,
      PlantRepository plantRepository,
      ReminderService reminderService) {
    this.careLogRepository = careLogRepository;
    this.reminderRepository = reminderRepository;
    this.plantRepository = plantRepository;
    this.reminderService = reminderService;
  }

  @Override
  @Transactional
  public CareLogResponse logCare(MarkCareDoneRequest request, Long userId) {
    Reminder reminder =
        reminderRepository
            .findByIdAndUserId(request.getReminderId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Reminder not found"));

    Instant performedAt = Instant.now();

    CareLog careLog =
        CareLog.builder()
            .plantId(reminder.getPlantId())
            .userId(userId)
            .careType(reminder.getCareType())
            .notes(request.getNotes())
            .performedAt(performedAt)
            .build();
    careLog = careLogRepository.save(careLog);

    reminderService.applyCompletionToReminder(reminder, performedAt);

    log.info(
        "Care logged: plantId={}, careType={}, userId={}",
        reminder.getPlantId(),
        reminder.getCareType(),
        userId);

    String nickname =
        plantRepository.findById(reminder.getPlantId()).map(Plant::getNickname).orElse(null);
    return toResponse(careLog, nickname);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<CareLogResponse> getPlantCareLogs(Long plantId, Long userId, Pageable pageable) {
    Plant plant =
        plantRepository
            .findByIdAndUserId(plantId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));

    return careLogRepository
        .findByPlantIdOrderByPerformedAtDesc(plantId, pageable)
        .map(careLog -> toResponse(careLog, plant.getNickname()));
  }

  private CareLogResponse toResponse(CareLog careLog, String plantNickname) {
    return CareLogResponse.builder()
        .id(careLog.getId())
        .plantId(careLog.getPlantId())
        .plantNickname(plantNickname)
        .careType(careLog.getCareType())
        .notes(careLog.getNotes())
        .performedAt(careLog.getPerformedAt())
        .build();
  }
}
