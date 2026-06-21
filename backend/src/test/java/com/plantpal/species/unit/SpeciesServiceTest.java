package com.plantpal.species.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.dto.CareCardDto;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.dto.SpeciesSummaryDto;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.mapper.SpeciesMapper;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesEnrichmentService;
import com.plantpal.species.service.impl.SpeciesServiceImpl;
import com.plantpal.user.entity.AiModelPreference;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpeciesService — Unit Tests")
class SpeciesServiceTest {

  @Mock private SpeciesRepository speciesRepository;
  @Mock private SpeciesMapper speciesMapper;
  @Mock private SpeciesEnrichmentService speciesEnrichmentService;
  @Mock private PlantRepository plantRepository;
  @Mock private IdentificationRepository identificationRepository;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  // Optional<SpeciesEnrichmentService> constructor param isn't @InjectMocks-friendly —
  // construct manually, same pattern as other services with non-mockable constructor params.
  private SpeciesServiceImpl speciesService;

  @BeforeEach
  void setUp() {
    speciesService =
        new SpeciesServiceImpl(
            speciesRepository,
            speciesMapper,
            Optional.of(speciesEnrichmentService),
            plantRepository,
            identificationRepository,
            objectMapper);
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
      Species result =
          speciesService.findOrCreate(
              "Monstera deliciosa", "Swiss Cheese Plant", AiModelPreference.DEEPSEEK);

      // Then
      assertThat(result.getId()).isEqualTo(1L);
      verify(speciesRepository, never()).save(any(Species.class));
      verify(speciesEnrichmentService, never()).enrich(any(), any());
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
      Species result =
          speciesService.findOrCreate(
              "Ficus lyrata", "Fiddle Leaf Fig", AiModelPreference.DEEPSEEK);

      // Then
      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      assertThat(captor.getValue().getScientificName()).isEqualTo("Ficus lyrata");
      assertThat(captor.getValue().getCommonName()).isEqualTo("Fiddle Leaf Fig");
      assertThat(captor.getValue().getStatus()).isEqualTo(SpeciesStatus.ACTIVE);

      // Enrichment fired — only the call is verified, not its (T6.4) result.
      verify(speciesEnrichmentService).enrich(7L, AiModelPreference.DEEPSEEK);

      assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("should forward the triggering user's AI model preference to enrichment")
    void shouldForwardAiModelPreferenceToEnrichment() {
      // Given
      when(speciesRepository.findByScientificName("Ficus lyrata")).thenReturn(Optional.empty());
      Species saved = Species.builder().id(7L).scientificName("Ficus lyrata").build();
      when(speciesRepository.save(any(Species.class))).thenReturn(saved);

      // When
      speciesService.findOrCreate(
          "Ficus lyrata", "Fiddle Leaf Fig", AiModelPreference.OLLAMA_LLAVA);

      // Then
      verify(speciesEnrichmentService).enrich(7L, AiModelPreference.OLLAMA_LLAVA);
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
      assertThat(result.getCareCards()).isEmpty();
    }

    @Test
    @DisplayName("should parse stored careCards JSON into the response")
    void shouldParseCareCardsWhenPresent() {
      // Given
      Species species =
          Species.builder()
              .id(1L)
              .scientificName("Monstera deliciosa")
              .careCards(
                  """
                  [{"type":"WATERING","title":"Watering","icon":"water_drop",\
                  "summary":"Water weekly","urgency":"MEDIUM"}]
                  """)
              .build();
      when(speciesRepository.findById(1L)).thenReturn(Optional.of(species));
      when(speciesMapper.toResponse(species)).thenReturn(SpeciesResponse.builder().id(1L).build());

      // When
      SpeciesResponse result = speciesService.getSpecies(1L);

      // Then
      assertThat(result.getCareCards()).hasSize(1);
      CareCardDto card = result.getCareCards().get(0);
      assertThat(card.getType()).isEqualTo("WATERING");
      assertThat(card.getTitle()).isEqualTo("Watering");
    }

    @Test
    @DisplayName("should degrade to an empty list on malformed careCards JSON, not throw")
    void shouldDegradeGracefullyOnMalformedCareCards() {
      // Given
      Species species =
          Species.builder()
              .id(1L)
              .scientificName("Monstera deliciosa")
              .careCards("not valid json {{{")
              .build();
      when(speciesRepository.findById(1L)).thenReturn(Optional.of(species));
      when(speciesMapper.toResponse(species)).thenReturn(SpeciesResponse.builder().id(1L).build());

      // When
      SpeciesResponse result = speciesService.getSpecies(1L);

      // Then
      assertThat(result.getCareCards()).isEmpty();
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
    @DisplayName("should return an empty page when the user has no plants with a speciesId")
    void shouldReturnEmptyPageWhenNoSpeciesIds() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
              1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(Page.empty(pageable));

      // When
      Page<SpeciesSummaryDto> result = speciesService.getUserSpecies(1L, pageable);

      // Then
      assertThat(result.getTotalElements()).isZero();
      assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName(
        "should group plants by speciesId, computing plantCount and an 'All healthy' summary")
    void shouldGroupPlantsBySpeciesWithAllHealthy() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
              1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(new PageImpl<>(List.of(50L), pageable, 1));

      Plant plantA = Plant.builder().id(10L).userId(1L).speciesId(50L).build();
      Plant plantB = Plant.builder().id(11L).userId(1L).speciesId(50L).build();
      when(plantRepository.findAllByUserIdAndStatusAndSpeciesIdIn(
              1L, PlantStatus.ACTIVE, List.of(50L)))
          .thenReturn(List.of(plantA, plantB));

      when(identificationRepository.findLatestPerPlant(List.of(10L, 11L)))
          .thenReturn(
              List.of(
                  Identification.builder().plantId(10L).healthStatus("HEALTHY").build(),
                  Identification.builder().plantId(11L).healthStatus("HEALTHY").build()));

      Species species =
          Species.builder()
              .id(50L)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss Cheese Plant")
              .imageUrl("https://example.com/monstera.jpg")
              .build();
      when(speciesRepository.findAllById(List.of(50L))).thenReturn(List.of(species));

      // When
      Page<SpeciesSummaryDto> result = speciesService.getUserSpecies(1L, pageable);

      // Then
      assertThat(result.getTotalElements()).isEqualTo(1);
      SpeciesSummaryDto summary = result.getContent().get(0);
      assertThat(summary.getSpeciesId()).isEqualTo(50L);
      assertThat(summary.getScientificName()).isEqualTo("Monstera deliciosa");
      assertThat(summary.getPlantCount()).isEqualTo(2);
      assertThat(summary.getHealthSummary()).isEqualTo("All healthy");
    }

    @Test
    @DisplayName("should report issue count in the healthSummary when a plant has ISSUES_DETECTED")
    void shouldReportIssueCountInHealthSummary() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
              1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(new PageImpl<>(List.of(50L), pageable, 1));

      Plant plantA = Plant.builder().id(10L).userId(1L).speciesId(50L).build();
      when(plantRepository.findAllByUserIdAndStatusAndSpeciesIdIn(
              1L, PlantStatus.ACTIVE, List.of(50L)))
          .thenReturn(List.of(plantA));

      when(identificationRepository.findLatestPerPlant(List.of(10L)))
          .thenReturn(
              List.of(
                  Identification.builder().plantId(10L).healthStatus("ISSUES_DETECTED").build()));

      Species species = Species.builder().id(50L).scientificName("Ficus lyrata").build();
      when(speciesRepository.findAllById(List.of(50L))).thenReturn(List.of(species));

      // When
      Page<SpeciesSummaryDto> result = speciesService.getUserSpecies(1L, pageable);

      // Then
      assertThat(result.getContent().get(0).getHealthSummary()).isEqualTo("1 issue(s)");
    }

    @Test
    @DisplayName("should report the most recent scan across the species' plants as lastScanAt")
    void shouldComputeMostRecentLastScanAt() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
              1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(new PageImpl<>(List.of(50L), pageable, 1));

      Plant plantA = Plant.builder().id(10L).userId(1L).speciesId(50L).build();
      Plant plantB = Plant.builder().id(11L).userId(1L).speciesId(50L).build();
      when(plantRepository.findAllByUserIdAndStatusAndSpeciesIdIn(
              1L, PlantStatus.ACTIVE, List.of(50L)))
          .thenReturn(List.of(plantA, plantB));

      Identification olderScan =
          Identification.builder().plantId(10L).healthStatus("HEALTHY").build();
      olderScan.setCreatedAt(java.time.Instant.parse("2026-06-01T00:00:00Z"));
      Identification newerScan =
          Identification.builder().plantId(11L).healthStatus("HEALTHY").build();
      newerScan.setCreatedAt(java.time.Instant.parse("2026-06-15T00:00:00Z"));
      when(identificationRepository.findLatestPerPlant(List.of(10L, 11L)))
          .thenReturn(List.of(olderScan, newerScan));

      Species species = Species.builder().id(50L).scientificName("Monstera deliciosa").build();
      when(speciesRepository.findAllById(List.of(50L))).thenReturn(List.of(species));

      // When
      Page<SpeciesSummaryDto> result = speciesService.getUserSpecies(1L, pageable);

      // Then
      assertThat(result.getContent().get(0).getLastScanAt())
          .isEqualTo(java.time.Instant.parse("2026-06-15T00:00:00Z"));
    }

    @Test
    @DisplayName("should report a null lastScanAt when no scans have a createdAt set")
    void shouldReportNullLastScanAtWhenAbsent() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
              1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(new PageImpl<>(List.of(50L), pageable, 1));

      Plant plantA = Plant.builder().id(10L).userId(1L).speciesId(50L).build();
      when(plantRepository.findAllByUserIdAndStatusAndSpeciesIdIn(
              1L, PlantStatus.ACTIVE, List.of(50L)))
          .thenReturn(List.of(plantA));

      when(identificationRepository.findLatestPerPlant(List.of(10L)))
          .thenReturn(
              List.of(Identification.builder().plantId(10L).healthStatus("HEALTHY").build()));

      Species species = Species.builder().id(50L).scientificName("Monstera deliciosa").build();
      when(speciesRepository.findAllById(List.of(50L))).thenReturn(List.of(species));

      // When
      Page<SpeciesSummaryDto> result = speciesService.getUserSpecies(1L, pageable);

      // Then
      assertThat(result.getContent().get(0).getLastScanAt()).isNull();
    }
  }
}
