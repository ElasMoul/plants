package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.dto.DiagramDto;
import com.plantpal.identification.dto.TreatmentStepDto;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.dto.TreatmentPlanResponse;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.entity.TreatmentPlan;
import com.plantpal.reminder.entity.TreatmentPlanStatus;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.repository.TreatmentPlanRepository;
import com.plantpal.reminder.service.impl.TreatmentPlanServiceImpl;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("TreatmentPlanService — Unit Tests")
class TreatmentPlanServiceTest {

  @Mock private TreatmentPlanRepository treatmentPlanRepository;
  @Mock private ReminderRepository reminderRepository;
  @Mock private PlantRepository plantRepository;

  private TreatmentPlanServiceImpl treatmentPlanService;

  private static final Long USER_ID = 1L;
  private static final Long PLANT_ID = 10L;

  @BeforeEach
  void setUp() {
    treatmentPlanService =
        new TreatmentPlanServiceImpl(treatmentPlanRepository, reminderRepository, plantRepository);
  }

  private Plant plant() {
    return Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("Monty").build();
  }

  private TreatmentStepDto step(int order, int dueOffsetDays) {
    return TreatmentStepDto.builder()
        .order(order)
        .instruction("Step " + order)
        .dueOffsetDays(dueOffsetDays)
        .build();
  }

  private ActionPlanDto treatmentActionPlan(TreatmentStepDto... steps) {
    return ActionPlanDto.builder().type("TREATMENT").steps(List.of(steps)).build();
  }

  @Nested
  @DisplayName("createFromActionPlan()")
  class CreateFromActionPlan {

    @Test
    @DisplayName("should create one reminder per step with nextDueAt matching dueOffsetDays")
    void shouldCreateOneReminderPerStep() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant()));
      when(treatmentPlanRepository.save(any(TreatmentPlan.class)))
          .thenAnswer(
              inv -> {
                TreatmentPlan saved = inv.getArgument(0, TreatmentPlan.class);
                saved.setId(500L);
                return saved;
              });
      when(reminderRepository.findByTreatmentPlanIdOrderByStepOrder(500L)).thenReturn(List.of());

      ActionPlanDto actionPlan = treatmentActionPlan(step(1, 0), step(2, 3), step(3, 7));

      TreatmentPlanResponse response =
          treatmentPlanService.createFromActionPlan(
              PLANT_ID, USER_ID, "Treat root rot", "PEST", actionPlan);

      assertThat(response.getId()).isEqualTo(500L);
      assertThat(response.getPlantId()).isEqualTo(PLANT_ID);
      assertThat(response.getTitle()).isEqualTo("Treat root rot");
      assertThat(response.getStatus()).isEqualTo("ACTIVE");

      ArgumentCaptor<Reminder> reminderCaptor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository, org.mockito.Mockito.times(3)).save(reminderCaptor.capture());
      List<Reminder> savedReminders = reminderCaptor.getAllValues();

      assertThat(savedReminders).hasSize(3);
      assertThat(savedReminders).allMatch(r -> r.getPlantId().equals(PLANT_ID));
      assertThat(savedReminders).allMatch(r -> r.getUserId().equals(USER_ID));
      assertThat(savedReminders).allMatch(r -> r.getCareType() == CareType.PEST);
      assertThat(savedReminders).allMatch(r -> !r.isRecurring());
      assertThat(savedReminders).allMatch(r -> r.getTreatmentPlanId().equals(500L));
      assertThat(savedReminders).allMatch(r -> r.getTreatmentPlanTitle().equals("Treat root rot"));
      assertThat(savedReminders.get(0).getStepOrder()).isEqualTo(1);
      assertThat(savedReminders.get(1).getStepOrder()).isEqualTo(2);
      assertThat(savedReminders.get(2).getStepOrder()).isEqualTo(3);

      // nextDueAt should be in ascending order matching ascending dueOffsetDays
      assertThat(savedReminders.get(0).getNextDueAt())
          .isBeforeOrEqualTo(savedReminders.get(1).getNextDueAt());
      assertThat(savedReminders.get(1).getNextDueAt())
          .isBeforeOrEqualTo(savedReminders.get(2).getNextDueAt());

      Instant now = Instant.now();
      assertThat(savedReminders.get(1).getNextDueAt())
          .isCloseTo(
              now.plus(3, ChronoUnit.DAYS),
              org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
      assertThat(savedReminders.get(2).getNextDueAt())
          .isCloseTo(
              now.plus(7, ChronoUnit.DAYS),
              org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("should reject a ROUTINE action plan")
    void shouldRejectRoutineActionPlan() {
      ActionPlanDto routine = ActionPlanDto.builder().type("ROUTINE").frequencyDays(7).build();

      assertThatThrownBy(
              () ->
                  treatmentPlanService.createFromActionPlan(
                      PLANT_ID, USER_ID, "Watering", "WATERING", routine))
          .isInstanceOf(ValidationException.class);

      verify(plantRepository, never()).findByIdAndUserId(any(), any());
      verify(reminderRepository, never()).save(any());
    }

    @Test
    @DisplayName("should reject a null action plan")
    void shouldRejectNullActionPlan() {
      assertThatThrownBy(
              () ->
                  treatmentPlanService.createFromActionPlan(
                      PLANT_ID, USER_ID, "Treat root rot", "PEST", null))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("should reject an action plan with no steps")
    void shouldRejectActionPlanWithNoSteps() {
      ActionPlanDto noSteps = ActionPlanDto.builder().type("TREATMENT").steps(List.of()).build();

      assertThatThrownBy(
              () ->
                  treatmentPlanService.createFromActionPlan(
                      PLANT_ID, USER_ID, "Treat root rot", "PEST", noSteps))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when the plant is not owned by the user")
    void shouldThrowWhenPlantNotOwned() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.empty());
      ActionPlanDto actionPlan = treatmentActionPlan(step(1, 0));

      assertThatThrownBy(
              () ->
                  treatmentPlanService.createFromActionPlan(
                      PLANT_ID, USER_ID, "Treat root rot", "PEST", actionPlan))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(treatmentPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("should reject a missing sourceCareCardType")
    void shouldRejectMissingSourceCareCardType() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant()));
      ActionPlanDto actionPlan = treatmentActionPlan(step(1, 0));

      assertThatThrownBy(
              () ->
                  treatmentPlanService.createFromActionPlan(
                      PLANT_ID, USER_ID, "Treat root rot", null, actionPlan))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("should reject an unrecognised sourceCareCardType")
    void shouldRejectUnknownSourceCareCardType() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant()));
      ActionPlanDto actionPlan = treatmentActionPlan(step(1, 0));

      assertThatThrownBy(
              () ->
                  treatmentPlanService.createFromActionPlan(
                      PLANT_ID, USER_ID, "Treat root rot", "NOT_A_TYPE", actionPlan))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("should persist the diagram format and content from the action plan")
    void shouldPersistDiagram() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant()));
      when(treatmentPlanRepository.save(any(TreatmentPlan.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(reminderRepository.findByTreatmentPlanIdOrderByStepOrder(any())).thenReturn(List.of());

      DiagramDto diagram =
          DiagramDto.builder().format("MERMAID").content("graph TD; A-->B;").build();
      ActionPlanDto actionPlan =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(diagram)
              .build();

      treatmentPlanService.createFromActionPlan(
          PLANT_ID, USER_ID, "Treat root rot", "PEST", actionPlan);

      ArgumentCaptor<TreatmentPlan> planCaptor = ArgumentCaptor.forClass(TreatmentPlan.class);
      verify(treatmentPlanRepository).save(planCaptor.capture());
      assertThat(planCaptor.getValue().getDiagramFormat()).isEqualTo("MERMAID");
      assertThat(planCaptor.getValue().getDiagramContent()).isEqualTo("graph TD; A-->B;");
    }
  }

  @Nested
  @DisplayName("getTreatmentPlan()")
  class GetTreatmentPlan {

    @Test
    @DisplayName("should return the plan with its steps ordered by stepOrder")
    void shouldReturnPlanWithOrderedSteps() {
      TreatmentPlan plan =
          TreatmentPlan.builder()
              .id(500L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .title("Treat root rot")
              .status(TreatmentPlanStatus.ACTIVE)
              .build();
      when(treatmentPlanRepository.findByIdAndUserId(500L, USER_ID)).thenReturn(Optional.of(plan));
      when(plantRepository.findById(PLANT_ID)).thenReturn(Optional.of(plant()));

      Reminder step1 =
          Reminder.builder().id(1L).plantId(PLANT_ID).treatmentPlanId(500L).stepOrder(1).build();
      Reminder step2 =
          Reminder.builder().id(2L).plantId(PLANT_ID).treatmentPlanId(500L).stepOrder(2).build();
      when(reminderRepository.findByTreatmentPlanIdOrderByStepOrder(500L))
          .thenReturn(List.of(step1, step2));

      TreatmentPlanResponse response = treatmentPlanService.getTreatmentPlan(500L, USER_ID);

      assertThat(response.getId()).isEqualTo(500L);
      assertThat(response.getSteps()).hasSize(2);
      assertThat(response.getSteps().get(0).getStepOrder()).isEqualTo(1);
      assertThat(response.getSteps().get(1).getStepOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when the plan is not owned by the user")
    void shouldThrowWhenPlanNotOwned() {
      when(treatmentPlanRepository.findByIdAndUserId(500L, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> treatmentPlanService.getTreatmentPlan(500L, USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
