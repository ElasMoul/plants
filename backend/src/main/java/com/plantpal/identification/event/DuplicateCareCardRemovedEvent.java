package com.plantpal.identification.event;

/**
 * Published when the background duplicate-care-card check (triggered after {@code addCareCard()})
 * removes a stale PEST care card for a plant+disease pair. Lets {@code com.plantpal.treatment}
 * react (dismissing any active Treatment for that disease) without {@code
 * com.plantpal.identification} depending on {@code com.plantpal.treatment} directly — {@code
 * com.plantpal.treatment} already depends on {@code com.plantpal.identification} (DeepSeekClient,
 * ActionPlanDto), so the reverse dependency would create a package cycle.
 */
public class DuplicateCareCardRemovedEvent {

  private final Long plantId;
  private final String diseaseName;

  public DuplicateCareCardRemovedEvent(Long plantId, String diseaseName) {
    this.plantId = plantId;
    this.diseaseName = diseaseName;
  }

  public Long getPlantId() {
    return plantId;
  }

  public String getDiseaseName() {
    return diseaseName;
  }
}
