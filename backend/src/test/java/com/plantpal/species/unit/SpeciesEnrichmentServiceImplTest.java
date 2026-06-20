package com.plantpal.species.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.impl.SpeciesEnrichmentServiceImpl;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("SpeciesEnrichmentService — Unit Tests")
class SpeciesEnrichmentServiceImplTest {

  private static final Long SPECIES_ID = 1L;

  @Mock private SpeciesRepository speciesRepository;
  @Mock private DeepSeekClient deepSeekClient;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  private SpeciesEnrichmentServiceImpl enrichmentService;

  private Species draftSpecies() {
    return Species.builder()
        .id(SPECIES_ID)
        .scientificName("Monstera deliciosa")
        .commonName("Swiss Cheese Plant")
        .status(SpeciesStatus.ACTIVE)
        .build();
  }

  @BeforeEach
  void setUp() {
    enrichmentService =
        new SpeciesEnrichmentServiceImpl(speciesRepository, deepSeekClient, objectMapper);
  }

  @Nested
  @DisplayName("enrich()")
  class Enrich {

    @Test
    @DisplayName("should populate all fields and keep status ACTIVE on a successful AI response")
    void shouldPopulateFieldsOnSuccess() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      when(deepSeekClient.generateSpeciesEnrichment("Monstera deliciosa", "Swiss Cheese Plant"))
          .thenReturn(
              """
              {"description":"A climbing tropical plant.",\
              "careOverview":"Bright indirect light, water weekly.",\
              "imageUrl":"https://example.com/monstera.jpg","source":"AI"}
              """);
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID)).doesNotThrowAnyException();

      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      Species saved = captor.getValue();
      assertThat(saved.getDescription()).isEqualTo("A climbing tropical plant.");
      assertThat(saved.getCareOverview()).isEqualTo("Bright indirect light, water weekly.");
      assertThat(saved.getImageUrl()).isEqualTo("https://example.com/monstera.jpg");
      assertThat(saved.getExternalDataSource()).isEqualTo("AI");
      assertThat(saved.getExternalDataFetchedAt()).isNotNull();
      assertThat(saved.getStatus()).isEqualTo(SpeciesStatus.ACTIVE);
    }

    @Test
    @DisplayName("should flip to NEEDS_REVIEW and leave fields null on malformed JSON")
    void shouldFlipToNeedsReviewOnMalformedJson() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      when(deepSeekClient.generateSpeciesEnrichment(any(), any())).thenReturn("not valid json {{{");
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID)).doesNotThrowAnyException();

      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      Species saved = captor.getValue();
      assertThat(saved.getStatus()).isEqualTo(SpeciesStatus.NEEDS_REVIEW);
      assertThat(saved.getDescription()).isNull();
      assertThat(saved.getCareOverview()).isNull();
      assertThat(saved.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("should flip to NEEDS_REVIEW and never propagate when the AI client throws")
    void shouldFlipToNeedsReviewWhenAiClientThrows() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      when(deepSeekClient.generateSpeciesEnrichment(any(), any()))
          .thenThrow(new PlantPalException("Species enrichment unavailable", 503));
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID)).doesNotThrowAnyException();

      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(SpeciesStatus.NEEDS_REVIEW);
    }

    @Test
    @DisplayName("should log and return cleanly without saving when the species is not found")
    void shouldReturnCleanlyWhenSpeciesNotFound() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.empty());

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID)).doesNotThrowAnyException();

      verify(speciesRepository, never()).save(any());
      verify(deepSeekClient, never()).generateSpeciesEnrichment(any(), any());
    }
  }
}
