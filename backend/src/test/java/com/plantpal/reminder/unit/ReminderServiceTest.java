package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.dto.CreateReminderRequest;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.entity.TreatmentPlan;
import com.plantpal.reminder.entity.TreatmentPlanStatus;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.repository.TreatmentPlanRepository;
import com.plantpal.reminder.service.impl.ReminderServiceImpl;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderService — Unit Tests")
class ReminderServiceTest {

  @Mock private ReminderRepository reminderRepository;
  @Mock private PlantRepository plantRepository;
  @Mock private CareLogRepository careLogRepository;
  @Mock private TreatmentPlanRepository treatmentPlanRepository;

  private ReminderServiceImpl reminderService;

  private static final Long USER_ID = 1L;
  private static final Long PLANT_ID = 10L;

  @BeforeEach
  void setUp() {
    reminderService =
        new ReminderServiceImpl(
            reminderRepository, plantRepository, careLogRepository, treatmentPlanRepository);
  }

  private Plant plant() {
    return Plant.builder()
        .id(PLANT_ID)
        .userId(USER_ID)
        .nickname("Monty")
        .photoUrl("/photos/monty.jpg")
        .build();
  }

  @Nested
  @DisplayName("createReminder()")
  class CreateReminder {

    @Test
    @DisplayName("should create a reminder for an owned plant")
    void shouldCreateReminderForOwnedPlant() {
      CreateReminderRequest request = new CreateReminderRequest();
      request.setPlantId(PLANT_ID);
      request.setCareType(CareType.WATERING);
      request.setFrequencyDays(7);
      Instant firstDueAt = Instant.parse("2026-07-01T08:00:00Z");
      request.setFirstDueAt(firstDueAt);

      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant()));
      when(reminderRepository.save(any(Reminder.class)))
          .thenAnswer(
              inv -> {
                Reminder saved = inv.getArgument(0, Reminder.class);
                saved.setId(99L);
                return saved;
              });

      ReminderResponse response = reminderService.createReminder(request, USER_ID);

      assertThat(response.getId()).isEqualTo(99L);
      assertThat(response.getPlantId()).isEqualTo(PLANT_ID);
      assertThat(response.getPlantNickname()).isEqualTo("Monty");
      assertThat(response.getPlantPhotoUrl()).isEqualTo("/photos/monty.jpg");
      assertThat(response.getCareType()).isEqualTo(CareType.WATERING);
      assertThat(response.getFrequencyDays()).isEqualTo(7);
      assertThat(response.getNextDueAt()).isEqualTo(firstDueAt);
      assertThat(response.isEnabled()).isTrue();

      ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository).save(captor.capture());
      assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
      assertThat(captor.getValue().getNextDueAt()).isEqualTo(firstDueAt);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when plant is not owned by user")
    void shouldThrowWhenPlantNotOwned() {
      CreateReminderRequest request = new CreateReminderRequest();
      request.setPlantId(PLANT_ID);
      request.setCareType(CareType.WATERING);
      request.setFrequencyDays(7);
      request.setFirstDueAt(Instant.now());

      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> reminderService.createReminder(request, USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getUserReminders()")
  class GetUserReminders {

    @Test
    @DisplayName("should return a list of reminders enriched with plant nickname and photo")
    void shouldReturnEnrichedReminderList() {
      Reminder reminder =
          Reminder.builder()
              .id(1L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .frequencyDays(7)
              .nextDueAt(Instant.now())
              .enabled(true)
              .build();

      Pageable expectedPageable = PageRequest.of(0, 200, Sort.by("nextDueAt").ascending());
      when(reminderRepository.findByUserIdAndEnabledTrue(USER_ID, expectedPageable))
          .thenReturn(new PageImpl<>(List.of(reminder)));
      when(plantRepository.findAllById(List.of(PLANT_ID))).thenReturn(List.of(plant()));

      List<ReminderResponse> result = reminderService.getUserReminders(USER_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getPlantNickname()).isEqualTo("Monty");
      assertThat(result.get(0).getPlantPhotoUrl()).isEqualTo("/photos/monty.jpg");
    }
  }

  @Nested
  @DisplayName("completeReminder()")
  class CompleteReminder {

    @Test
    @DisplayName("should log care, reschedule nextDueAt, and return the updated reminder")
    void shouldCompleteAndRescheduleReminder() {
      Reminder reminder =
          Reminder.builder()
              .id(1L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .frequencyDays(7)
              .nextDueAt(Instant.parse("2026-06-18T08:00:00Z"))
              .enabled(true)
              .build();
      when(reminderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(reminder));
      when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));
      when(plantRepository.findById(PLANT_ID)).thenReturn(Optional.of(plant()));

      ReminderResponse response = reminderService.completeReminder(1L, USER_ID);

      ArgumentCaptor<CareLog> careLogCaptor = ArgumentCaptor.forClass(CareLog.class);
      verify(careLogRepository).save(careLogCaptor.capture());
      assertThat(careLogCaptor.getValue().getPlantId()).isEqualTo(PLANT_ID);
      assertThat(careLogCaptor.getValue().getCareType()).isEqualTo(CareType.WATERING);

      assertThat(response.getNextDueAt()).isAfter(Instant.parse("2026-06-18T08:00:00Z"));
      assertThat(response.getPlantNickname()).isEqualTo("Monty");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when reminder is not owned by user")
    void shouldThrowWhenReminderNotOwned() {
      when(reminderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> reminderService.completeReminder(1L, USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(careLogRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("deleteReminder()")
  class DeleteReminder {

    @Test
    @DisplayName("should disable the reminder rather than hard-deleting it")
    void shouldDisableReminder() {
      Reminder reminder =
          Reminder.builder().id(1L).plantId(PLANT_ID).userId(USER_ID).enabled(true).build();
      when(reminderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(reminder));
      when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

      reminderService.deleteReminder(1L, USER_ID);

      ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository).save(captor.capture());
      assertThat(captor.getValue().isEnabled()).isFalse();
      verify(reminderRepository, never()).delete(any());
      verify(reminderRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when reminder does not belong to user")
    void shouldThrowWhenReminderNotOwned() {
      when(reminderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> reminderService.deleteReminder(1L, USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("calculateNextDueAt()")
  class CalculateNextDueAt {

    @Test
    @DisplayName("should add the frequency in days to the last-done instant")
    void shouldAddFrequencyDays() {
      Instant lastDone = Instant.parse("2026-06-18T08:00:00Z");

      Instant nextDueAt = reminderService.calculateNextDueAt(lastDone, 7);

      assertThat(nextDueAt).isEqualTo(lastDone.plus(7, ChronoUnit.DAYS));
      assertThat(nextDueAt).isEqualTo(Instant.parse("2026-06-25T08:00:00Z"));
    }

    @Test
    @DisplayName("should handle a zero-day frequency as no change")
    void shouldHandleZeroFrequency() {
      Instant lastDone = Instant.parse("2026-06-18T08:00:00Z");

      Instant nextDueAt = reminderService.calculateNextDueAt(lastDone, 0);

      assertThat(nextDueAt).isEqualTo(lastDone);
    }
  }

  @Nested
  @DisplayName("applyCompletionToReminder()")
  class ApplyCompletionToReminder {

    @Test
    @DisplayName("should reschedule nextDueAt and leave a recurring reminder enabled")
    void shouldRescheduleRecurringReminder() {
      Reminder reminder =
          Reminder.builder()
              .id(1L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .frequencyDays(7)
              .nextDueAt(Instant.parse("2026-06-18T08:00:00Z"))
              .enabled(true)
              .recurring(true)
              .build();
      Instant performedAt = Instant.parse("2026-06-18T08:00:00Z");

      reminderService.applyCompletionToReminder(reminder, performedAt);

      assertThat(reminder.isEnabled()).isTrue();
      assertThat(reminder.getNextDueAt()).isEqualTo(performedAt.plus(7, ChronoUnit.DAYS));
      verify(reminderRepository).save(reminder);
      verify(treatmentPlanRepository, never()).findById(any());
    }

    @Test
    @DisplayName("should disable a one-time reminder without touching a treatment plan")
    void shouldDisableOneTimeReminderWithNoPlan() {
      Reminder reminder =
          Reminder.builder()
              .id(1L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .careType(CareType.PEST)
              .enabled(true)
              .recurring(false)
              .build();

      reminderService.applyCompletionToReminder(reminder, Instant.now());

      assertThat(reminder.isEnabled()).isFalse();
      verify(reminderRepository).save(reminder);
      verify(treatmentPlanRepository, never()).findById(any());
    }

    @Test
    @DisplayName("should mark the treatment plan COMPLETED when the last step is finished")
    void shouldCompleteTreatmentPlanOnLastStep() {
      Reminder lastStep =
          Reminder.builder()
              .id(1L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .careType(CareType.PEST)
              .enabled(true)
              .recurring(false)
              .treatmentPlanId(50L)
              .stepOrder(3)
              .build();
      TreatmentPlan plan =
          TreatmentPlan.builder().id(50L).status(TreatmentPlanStatus.ACTIVE).build();

      when(reminderRepository.findByTreatmentPlanIdAndEnabledTrue(50L)).thenReturn(List.of());
      when(treatmentPlanRepository.findById(50L)).thenReturn(Optional.of(plan));

      reminderService.applyCompletionToReminder(lastStep, Instant.now());

      assertThat(lastStep.isEnabled()).isFalse();
      ArgumentCaptor<TreatmentPlan> planCaptor = ArgumentCaptor.forClass(TreatmentPlan.class);
      verify(treatmentPlanRepository).save(planCaptor.capture());
      assertThat(planCaptor.getValue().getStatus()).isEqualTo(TreatmentPlanStatus.COMPLETED);
    }

    @Test
    @DisplayName("should leave the treatment plan ACTIVE when steps remain")
    void shouldLeavePlanActiveWhenStepsRemain() {
      Reminder step =
          Reminder.builder()
              .id(1L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .careType(CareType.PEST)
              .enabled(true)
              .recurring(false)
              .treatmentPlanId(50L)
              .stepOrder(1)
              .build();
      Reminder remainingStep =
          Reminder.builder().id(2L).treatmentPlanId(50L).stepOrder(2).enabled(true).build();

      when(reminderRepository.findByTreatmentPlanIdAndEnabledTrue(50L))
          .thenReturn(List.of(remainingStep));

      reminderService.applyCompletionToReminder(step, Instant.now());

      assertThat(step.isEnabled()).isFalse();
      verify(treatmentPlanRepository, never()).findById(any());
      verify(treatmentPlanRepository, never()).save(any());
    }
  }
}
