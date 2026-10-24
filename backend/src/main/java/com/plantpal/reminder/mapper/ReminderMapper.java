package com.plantpal.reminder.mapper;

import com.plantpal.plant.entity.Plant;
import com.plantpal.reminder.CareIntervalCalculator;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.entity.Reminder;

/**
 * Single source of truth for {@link Reminder} → {@link ReminderResponse} mapping. Previously
 * duplicated independently in {@code ReminderServiceImpl} and {@code TreatmentPlanServiceImpl},
 * which is how {@code instruction}/{@code completedAt} were added to the DTO but only ever wired up
 * on the frontend — neither copy of the mapper actually populated them.
 */
public final class ReminderMapper {

  private ReminderMapper() {}

  public static ReminderResponse toResponse(Reminder reminder, Plant plant) {
    return toResponse(
        reminder,
        plant,
        new CareIntervalCalculator.CareInterval(reminder.getFrequencyDays(), null, null));
  }

  public static ReminderResponse toResponse(
      Reminder reminder, Plant plant, CareIntervalCalculator.CareInterval interval) {
    return ReminderResponse.builder()
        .id(reminder.getId())
        .plantId(reminder.getPlantId())
        .plantNickname(plant != null ? plant.getNickname() : null)
        .plantPhotoUrl(plant != null ? plant.getPhotoUrl() : null)
        .careType(reminder.getCareType())
        .frequencyDays(reminder.getFrequencyDays())
        .effectiveFrequencyDays(interval.days())
        .overrideFrequencyDays(reminder.getOverrideFrequencyDays())
        .season(interval.season())
        .adjustmentReason(interval.reason())
        .nextDueAt(reminder.getNextDueAt())
        .enabled(reminder.isEnabled())
        .recurring(reminder.isRecurring())
        .treatmentPlanId(reminder.getTreatmentPlanId())
        .treatmentPlanTitle(reminder.getTreatmentPlanTitle())
        .stepOrder(reminder.getStepOrder())
        .instruction(reminder.getInstruction())
        .completedAt(reminder.isEnabled() ? null : reminder.getUpdatedAt())
        .stepDetail(reminder.getStepDetail())
        .stepDiagramFormat(reminder.getStepDiagramFormat())
        .stepDiagramContent(reminder.getStepDiagramContent())
        .build();
  }
}
