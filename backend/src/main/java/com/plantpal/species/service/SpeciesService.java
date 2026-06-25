package com.plantpal.species.service;

import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.dto.SpeciesSummaryDto;
import com.plantpal.species.entity.Species;
import com.plantpal.user.entity.AiModelPreference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SpeciesService {

  Species findOrCreate(String scientificName, String commonName, AiModelPreference preference);

  Species findOrCreate(
      String scientificName,
      String commonName,
      AiModelPreference preference,
      String gbifId,
      String powoId,
      String iucnCategory);

  /** T9.A: also accepts botanical identity fields harvested from the confirmed PlantNet candidate. */
  Species findOrCreate(
      String scientificName,
      String commonName,
      AiModelPreference preference,
      String gbifId,
      String powoId,
      String iucnCategory,
      String family,
      String genus,
      String imageUrl,
      String imageAttribution,
      String imageLicense);

  SpeciesResponse getSpecies(Long id);

  Page<SpeciesSummaryDto> getUserSpecies(Long userId, Pageable pageable);

  /** T9.B: re-fire async prose enrichment; flips descriptionStatus back to PENDING. */
  SpeciesResponse regenerateDescription(Long speciesId, Long userId);
}
