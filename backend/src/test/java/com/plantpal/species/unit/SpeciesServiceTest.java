package com.plantpal.species.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.dto.SpeciesSummaryDto;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.mapper.SpeciesMapper;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesEnrichmentService;
import com.plantpal.species.service.impl.SpeciesServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpeciesService — Unit Tests")
class SpeciesServiceTest {

  @Mock private SpeciesRepository speciesRepository;
  @Mock private SpeciesMapper speciesMapper;
  @Mock private SpeciesEnrichmentService speciesEnrichmentService;

  // Optional<SpeciesEnrichmentService> constructor param isn't @InjectMocks-friendly —
  // construct manually, same pattern as other services with non-mockable constructor params.
  private SpeciesServiceImpl speciesService;

  @BeforeEach
  void setUp() {
    speciesService =
        new SpeciesServiceImpl(
            speciesRepository, speciesMapper, Optional.of(speciesEnrichmentService));
  }

  @Nested
  @DisplayName("findOrCreate()")
  class FindOrCreate {

    @Test
    @DisplayName("should return the existing row and never create a duplicate")
    void shouldReturnExistingSpeciesWithoutCreating() {
      // Given
      Species existing =
          Species.builder()
              .id(1L)
              .scientificName("Monstera deliciosa")
              .status(SpeciesStatus.ACTIVE)
              .build();
      when(speciesRepository.findByScientificName("Monstera deliciosa"))
          .thenReturn(Optional.of(existing));

      // When
      Species result = speciesService.findOrCreate("Monstera deliciosa", "Swiss Cheese Plant");

      // Then
      assertThat(result.getId()).isEqualTo(1L);
      verify(speciesRepository, never()).save(any(Species.class));
      verify(speciesEnrichmentService, never()).enrich(any());
    }

    @Test
    @DisplayName("should create a new ACTIVE row and fire enrichment when scientificName is unseen")
    void shouldCreateAndFireEnrichmentWhenNotFound() {
      // Given
      when(speciesRepository.findByScientificName("Ficus lyrata")).thenReturn(Optional.empty());
      Species saved =
          Species.builder()
              .id(7L)
              .scientificName("Ficus lyrata")
              .status(SpeciesStatus.ACTIVE)
              .build();
      when(speciesRepository.save(any(Species.class))).thenReturn(saved);

      // When
      Species result = speciesService.findOrCreate("Ficus lyrata", "Fiddle Leaf Fig");

      // Then
      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      assertThat(captor.getValue().getScientificName()).isEqualTo("Ficus lyrata");
      assertThat(captor.getValue().getCommonName()).isEqualTo("Fiddle Leaf Fig");
      assertThat(captor.getValue().getStatus()).isEqualTo(SpeciesStatus.ACTIVE);

      // Enrichment fired — only the call is verified, not its (T6.4) result.
      verify(speciesEnrichmentService).enrich(7L);

      assertThat(result.getId()).isEqualTo(7L);
    }
  }

  @Nested
  @DisplayName("getSpecies()")
  class GetSpecies {

    @Test
    @DisplayName("should return the species response when found, with no ownership check")
    void shouldReturnSpeciesWhenFound() {
      // Given
      Species species = Species.builder().id(1L).scientificName("Monstera deliciosa").build();
      SpeciesResponse response = SpeciesResponse.builder().id(1L).build();
      when(speciesRepository.findById(1L)).thenReturn(Optional.of(species));
      when(speciesMapper.toResponse(species)).thenReturn(response);

      // When
      SpeciesResponse result = speciesService.getSpecies(1L);

      // Then
      assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when the species does not exist")
    void shouldThrowWhenNotFound() {
      // Given
      when(speciesRepository.findById(99L)).thenReturn(Optional.empty());

      // When / Then
      assertThatThrownBy(() -> speciesService.getSpecies(99L))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getUserSpecies()")
  class GetUserSpecies {

    @Test
    @DisplayName(
        "should return an empty page — stubbed pending T6.3 (plants.species_id does not exist"
            + " yet); full grouping/plantCount/healthSummary coverage lands with that migration")
    void shouldReturnEmptyPageUntilSpeciesIdExistsOnPlant() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);

      // When
      Page<SpeciesSummaryDto> result = speciesService.getUserSpecies(1L, pageable);

      // Then
      assertThat(result.getTotalElements()).isZero();
      assertThat(result.getContent()).isEmpty();
    }
  }
}
