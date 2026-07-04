package com.plantpal.identification.entity;

/**
 * Per-stage outcome for a single pipeline stage (core identification, annotation, or PlantNet
 * candidates). Distinct from {@link IdentificationStatus} — adds SKIPPED for stages that were never
 * attempted (e.g. always-on PlantNet before it became async, or annotation when PLANTNET is the
 * primary model).
 */
public enum IdentificationStageStatus {
  PENDING,
  COMPLETED,
  FAILED,
  SKIPPED
}
