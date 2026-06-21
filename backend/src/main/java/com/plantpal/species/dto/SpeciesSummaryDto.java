package com.plantpal.species.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Garden species-first list — one row per distinct species the caller owns at least one plant of.
 */
@Getter
@Builder
public class SpeciesSummaryDto {

  private Long speciesId;

  private String scientificName;

  private String commonName;

  private String imageUrl;

  private int plantCount;

  /** Computed, not stored — e.g. "All healthy" or "N issue(s)". */
  private String healthSummary;

  /** Computed, not stored — the most recent scan across all of this species' plants, or null. */
  private Instant lastScanAt;
}
