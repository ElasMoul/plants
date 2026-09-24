package com.plantpal.localization;

/** A snapshot of newly generated prose, never emitted by read endpoints. */
public record GeneratedPlantText(Long userId, Object content) {}
