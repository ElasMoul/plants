package com.plantpal.plant.event;

/**
 * Published inside the transaction that creates or archives a {@code Plant}. Consumed by {@link
 * PlantCountDimensionEmitter} only AFTER the transaction commits, so a rollback can never leak a
 * phantom {@code plant_count} delta to Treasury (FIX-12).
 */
public class PlantCountChangedEvent {

  private final Long userId;
  private final int delta;

  public PlantCountChangedEvent(Long userId, int delta) {
    this.userId = userId;
    this.delta = delta;
  }

  public Long getUserId() {
    return userId;
  }

  public int getDelta() {
    return delta;
  }
}
