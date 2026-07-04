package com.plantpal.shared.entity;

/**
 * Tracks the lifecycle of a fire-and-forget async AI text field (e.g. Species.description,
 * Treatment.diseaseDescription). PENDING = generation queued/in-flight, READY = text is present,
 * FAILED = generation failed and text is null — the caller should offer a Retry.
 */
public enum GenerationStatus {
  PENDING,
  READY,
  FAILED
}
