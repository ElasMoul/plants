package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.dto.CareLogResponse;
import com.plantpal.reminder.dto.MarkCareDoneRequest;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.reminder.service.impl.CareLogServiceImpl;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareLogService — Unit Tests")
class CareLogServiceTest {

  @Mock private CareLogRepository careLogRepository;
  @Mock private ReminderRepository reminderRepository;
  @Mock private PlantRepository plantRepository;
  @Mock private ReminderService reminderService;

  private CareLogServiceImpl careLogService;

  private static final Long USER_ID = 1L;
  private static final Long PLANT_ID = 10L;
  private static final Long REMINDER_ID = 5L;

  @BeforeEach
  void setUp() {
    careLogService =
        new CareLogServiceImpl(
            careLogRepository, reminderRepository, plantRepository, reminderService);
  }

  private Plant plant() {
    return Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("Monty").build();
  }

  private Reminder reminder() {
    return Reminder.builder()
        .id(REMINDER_ID)
        .plantId(PLANT_ID)
        .userId(USER_ID)
        .careType(CareType.WATERING)
        .frequencyDays(7)
        .nextDueAt(Instant.parse("2026-06-18T08:00:00Z"))
        .enabled(true)
        .build();
  }

  @Nested
  @DisplayName("logCare()")
  class LogCare {

    @Test
    @DisplayName("should save a care log and delegate completion handling to ReminderService")
    void shouldLogCareAndDelegateToReminderService() {
      MarkCareDoneRequest request = new MarkCareDoneRequest();
      request.setReminderId(REMINDER_ID);
      request.setNotes("Gave it a good soak");

      Reminder reminder = reminder();
      when(reminderRepository.findByIdAndUserId(REMINDER_ID, USER_ID))
          .thenReturn(Optional.of(reminder));
      when(careLogRepository.save(any(CareLog.class)))
          .thenAnswer(
              inv -> {
                CareLog saved = inv.getArgument(0, CareLog.class);
                saved.setId(77L);
                return saved;
              });
      when(plantRepository.findById(PLANT_ID)).thenReturn(Optional.of(plant()));

      CareLogResponse response = careLogService.logCare(request, USER_ID);

      assertThat(response.getId()).isEqualTo(77L);
      assertThat(response.getPlantId()).isEqualTo(PLANT_ID);
      assertThat(response.getPlantNickname()).isEqualTo("Monty");
      assertThat(response.getCareType()).isEqualTo(CareType.WATERING);
      assertThat(response.getNotes()).isEqualTo("Gave it a good soak");

      // Rescheduling (or, for a one-time treatment step, disabling) is entirely
      // ReminderService's responsibility — CareLogServiceImpl must not duplicate that logic.
      verify(reminderService).applyCompletionToReminder(eq(reminder), any(Instant.class));
      verify(reminderService, never()).calculateNextDueAt(any(Instant.class), anyInt());
      verify(reminderRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when reminder is not owned by user")
    void shouldThrowWhenReminderNotOwned() {
      MarkCareDoneRequest request = new MarkCareDoneRequest();
      request.setReminderId(REMINDER_ID);

      when(reminderRepository.findByIdAndUserId(REMINDER_ID, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> careLogService.logCare(request, USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(careLogRepository, never()).save(any());
      verify(reminderService, never()).applyCompletionToReminder(any(), any());
    }
  }

  @Nested
  @DisplayName("getPlantCareLogs()")
  class GetPlantCareLogs {

    @Test
    @DisplayName("should return a page of care logs enriched with the plant nickname")
    void shouldReturnEnrichedCareLogPage() {
      Pageable pageable = PageRequest.of(0, 20);
      CareLog careLog =
          CareLog.builder()
              .id(1L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .notes("Watered")
              .performedAt(Instant.now())
              .build();

      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant()));
      when(careLogRepository.findByPlantIdOrderByPerformedAtDesc(PLANT_ID, pageable))
          .thenReturn(new PageImpl<>(List.of(careLog)));

      Page<CareLogResponse> result = careLogService.getPlantCareLogs(PLANT_ID, USER_ID, pageable);

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).getPlantNickname()).isEqualTo("Monty");
      assertThat(result.getContent().get(0).getNotes()).isEqualTo("Watered");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when plant is not owned by user")
    void shouldThrowWhenPlantNotOwned() {
      Pageable pageable = PageRequest.of(0, 20);
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> careLogService.getPlantCareLogs(PLANT_ID, USER_ID, pageable))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
