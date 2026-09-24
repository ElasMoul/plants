package com.plantpal.localization;

public record GeneratedPlantText(String kind, Long resourceId, Long userId, String section) {
  public GeneratedPlantText(String kind, Long resourceId, Long userId) {
    this(kind, resourceId, userId, null);
  }
}
