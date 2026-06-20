package com.plantpal.reminder.event;

/**
 * Published when a {@code TreatmentPlan}'s last enabled step is completed and its status flips to
 * COMPLETED. Lets {@code com.plantpal.treatment} react (syncing its own {@code Treatment} entity)
 * without {@code com.plantpal.reminder} depending on {@code com.plantpal.treatment} — avoids the
 * package cycle that direct injection of TreatmentService into ReminderServiceImpl would create.
 */
public class TreatmentPlanCompletedEvent {

  private final Long treatmentPlanId;

  public TreatmentPlanCompletedEvent(Long treatmentPlanId) {
    this.treatmentPlanId = treatmentPlanId;
  }

  public Long getTreatmentPlanId() {
    return treatmentPlanId;
  }
}
