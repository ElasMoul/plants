package com.plantpal.species.service;

import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.dto.SpeciesSummaryDto;
import com.plantpal.species.entity.Species;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SpeciesService {

  Species findOrCreate(String scientificName, String commonName);

  SpeciesResponse getSpecies(Long id);

  Page<SpeciesSummaryDto> getUserSpecies(Long userId, Pageable pageable);
}
