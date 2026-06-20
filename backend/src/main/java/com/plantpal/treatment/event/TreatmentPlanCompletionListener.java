package com.plantpal.treatment.event;

import com.plantpal.reminder.event.TreatmentPlanCompletedEvent;
import com.plantpal.treatment.service.TreatmentService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to {@link TreatmentPlanCompletedEvent} (published by {@code ReminderServiceImpl} when a
 * TreatmentPlan's last step is done) by syncing the wrapping {@code Treatment}'s status. Exists so
 * {@code com.plantpal.reminder} never has to depend on {@code com.plantpal.treatment} directly —
 * see {@link TreatmentService#syncFromTreatmentPlanCompletion} for the package-cycle rationale.
 */
@Component
public class TreatmentPlanCompletionListener {

  private final TreatmentService treatmentService;

  public TreatmentPlanCompletionListener(TreatmentService treatmentService) {
    this.treatmentService = treatmentService;
  }

  @EventListener
  public void onTreatmentPlanCompleted(TreatmentPlanCompletedEvent event) {
    treatmentService.syncFromTreatmentPlanCompletion(event.getTreatmentPlanId());
  }
}
