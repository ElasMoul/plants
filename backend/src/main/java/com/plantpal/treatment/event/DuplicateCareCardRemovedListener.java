package com.plantpal.treatment.event;

import com.plantpal.identification.event.DuplicateCareCardRemovedEvent;
import com.plantpal.treatment.service.TreatmentService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to {@link DuplicateCareCardRemovedEvent} (published by {@code IdentificationServiceImpl}
 * when the background duplicate-check removes a stale PEST care card) by dismissing any active
 * Treatment that was tracking the now-removed disease — see {@link DuplicateCareCardRemovedEvent}
 * for the package-cycle rationale.
 */
@Component
public class DuplicateCareCardRemovedListener {

  private final TreatmentService treatmentService;

  public DuplicateCareCardRemovedListener(TreatmentService treatmentService) {
    this.treatmentService = treatmentService;
  }

  @EventListener
  public void onDuplicateCareCardRemoved(DuplicateCareCardRemovedEvent event) {
    treatmentService.dismissActiveTreatmentForDisease(event.getPlantId(), event.getDiseaseName());
  }
}
