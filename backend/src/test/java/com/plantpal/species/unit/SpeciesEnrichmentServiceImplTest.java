package com.plantpal.species.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.shared.entity.GenerationStatus;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.impl.SpeciesEnrichmentServiceImpl;
import com.plantpal.user.entity.AiModelPreference;
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
  @Mock private OllamaClient ollamaClient;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();
  @Mock private com.plantpal.gateway.GatewayClient gatewayClient;

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
        new SpeciesEnrichmentServiceImpl(
            speciesRepository,
            deepSeekClient,
            ollamaClient,
            objectMapper,
            gatewayClient,
            new com.plantpal.gateway.GatewayProperties(false, "http://localhost:8085"));
  }

  /** Flips the gateway flag on for a single test. */
  private void enableGateway() {
    org.springframework.test.util.ReflectionTestUtils.setField(
        enrichmentService,
        "gatewayProperties",
        new com.plantpal.gateway.GatewayProperties(true, "http://localhost:8085"));
  }

  @Nested
  @DisplayName("enrich()")
  class Enrich {

    @Test
    @DisplayName("should populate prose fields and keep status ACTIVE on a successful AI response")
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

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID, AiModelPreference.DEEPSEEK))
          .doesNotThrowAnyException();

      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      Species saved = captor.getValue();
      assertThat(saved.getDescription()).isEqualTo("A climbing tropical plant.");
      assertThat(saved.getCareOverview()).isEqualTo("Bright indirect light, water weekly.");
      // AI enrichment intentionally does NOT set imageUrl (T9.B decision: only PlantNet sets it)
      assertThat(saved.getImageUrl()).isNull();
      assertThat(saved.getExternalDataSource()).isEqualTo("AI");
      assertThat(saved.getExternalDataFetchedAt()).isNotNull();
      assertThat(saved.getStatus()).isEqualTo(SpeciesStatus.ACTIVE);
    }

    @Test
    @DisplayName("should set descriptionStatus=FAILED and leave prose null on malformed JSON")
    void shouldFlipToNeedsReviewOnMalformedJson() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      when(deepSeekClient.generateSpeciesEnrichment(any(), any())).thenReturn("not valid json {{{");
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID, AiModelPreference.DEEPSEEK))
          .doesNotThrowAnyException();

      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      Species saved = captor.getValue();
      assertThat(saved.getStatus()).isEqualTo(SpeciesStatus.ACTIVE);
      assertThat(saved.getDescriptionStatus()).isEqualTo(GenerationStatus.FAILED);
      assertThat(saved.getDescription()).isNull();
      assertThat(saved.getCareOverview()).isNull();
      assertThat(saved.getImageUrl()).isNull();
    }

    @Test
    @DisplayName(
        "should set descriptionStatus=FAILED and never propagate when the AI client throws")
    void shouldFlipToNeedsReviewWhenAiClientThrows() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      when(deepSeekClient.generateSpeciesEnrichment(any(), any()))
          .thenThrow(new PlantPalException("Species enrichment unavailable", 503));
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID, AiModelPreference.DEEPSEEK))
          .doesNotThrowAnyException();

      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(SpeciesStatus.ACTIVE);
      assertThat(captor.getValue().getDescriptionStatus()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    @DisplayName("should log and return cleanly without saving when the species is not found")
    void shouldReturnCleanlyWhenSpeciesNotFound() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.empty());

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID, AiModelPreference.DEEPSEEK))
          .doesNotThrowAnyException();

      verify(speciesRepository, never()).save(any());
      verify(deepSeekClient, never()).generateSpeciesEnrichment(any(), any());
    }

    @Test
    @DisplayName(
        "should route through OllamaClient, not DeepSeekClient, for the OLLAMA_LLAVA preference")
    void shouldRouteThroughOllamaForOllamaPreference() {
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      when(ollamaClient.generateSpeciesEnrichment("Monstera deliciosa", "Swiss Cheese Plant"))
          .thenReturn(
              """
              {"description":"A climbing tropical plant.",\
              "careOverview":"Bright indirect light, water weekly.",\
              "imageUrl":"https://example.com/monstera.jpg","source":"AI"}
              """);
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> enrichmentService.enrich(SPECIES_ID, AiModelPreference.OLLAMA_LLAVA))
          .doesNotThrowAnyException();

      verify(deepSeekClient, never()).generateSpeciesEnrichment(any(), any());
      ArgumentCaptor<Species> captor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(SpeciesStatus.ACTIVE);
      assertThat(captor.getValue().getDescription()).isEqualTo("A climbing tropical plant.");
    }

    @Test
    @DisplayName(
        "should route DEEPSEEK preference through GatewayClient when the flag is on (D022,"
            + " Chunk 3)")
    void shouldRouteDeepSeekThroughGatewayWhenFlagOn() {
      enableGateway();
      when(deepSeekClient.getModel()).thenReturn("DeepSeek-R1");
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      io.platform.contracts.aigateway.AiResponse response =
          new io.platform.contracts.aigateway.AiResponse();
      response.setResult(
          """
          {"description":"A climbing tropical plant.",\
          "careOverview":"Bright indirect light, water weekly.",\
          "imageUrl":"https://example.com/monstera.jpg","source":"AI"}
          """);
      response.setModel("DeepSeek-R1");
      response.setProvider("deepseek");
      response.setTokensIn(1);
      response.setTokensOut(1);
      response.setComputedCost(java.math.BigDecimal.ZERO);
      when(gatewayClient.request(any())).thenReturn(response);
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      enrichmentService.enrich(SPECIES_ID, AiModelPreference.DEEPSEEK);

      verify(deepSeekClient, never()).generateSpeciesEnrichment(any(), any());
      ArgumentCaptor<io.platform.contracts.aigateway.AiRequest> captor =
          ArgumentCaptor.forClass(io.platform.contracts.aigateway.AiRequest.class);
      verify(gatewayClient).request(captor.capture());
      assertThat(captor.getValue().getAppId()).isEqualTo("plantpal");
      assertThat(captor.getValue().getModelHint()).isEqualTo("DeepSeek-R1");
      assertThat(captor.getValue().getContext())
          .containsEntry("systemPrompt", DeepSeekClient.SPECIES_ENRICHMENT_SYSTEM_PROMPT);
      ArgumentCaptor<Species> speciesCaptor = ArgumentCaptor.forClass(Species.class);
      verify(speciesRepository).save(speciesCaptor.capture());
      assertThat(speciesCaptor.getValue().getDescription()).isEqualTo("A climbing tropical plant.");
    }

    @Test
    @DisplayName(
        "should route OLLAMA_LLAVA preference through GatewayClient with configured Ollama model"
            + " when the flag is on (D022, Chunk 3)")
    void shouldRouteOllamaThroughGatewayWhenFlagOn() {
      enableGateway();
      when(ollamaClient.getModel()).thenReturn("gemma3:4b");
      when(speciesRepository.findById(SPECIES_ID)).thenReturn(Optional.of(draftSpecies()));
      io.platform.contracts.aigateway.AiResponse response =
          new io.platform.contracts.aigateway.AiResponse();
      response.setResult(
          """
          {"description":"A climbing tropical plant.",\
          "careOverview":"Bright indirect light, water weekly.",\
          "imageUrl":"https://example.com/monstera.jpg","source":"AI"}
          """);
      response.setModel("gemma3:4b");
      response.setProvider("ollama");
      response.setTokensIn(1);
      response.setTokensOut(1);
      response.setComputedCost(java.math.BigDecimal.ZERO);
      when(gatewayClient.request(any())).thenReturn(response);
      when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));

      enrichmentService.enrich(SPECIES_ID, AiModelPreference.OLLAMA_LLAVA);

      verify(ollamaClient, never()).generateSpeciesEnrichment(any(), any());
      ArgumentCaptor<io.platform.contracts.aigateway.AiRequest> captor =
          ArgumentCaptor.forClass(io.platform.contracts.aigateway.AiRequest.class);
      verify(gatewayClient).request(captor.capture());
      assertThat(captor.getValue().getModelHint()).isEqualTo("gemma3:4b");
    }
  }

  @Nested
  @DisplayName("species cache wiring (T-DEPLOY.2)")
  class SpeciesCacheWiring {

    @Test
    @DisplayName(
        "enrich() should evict \"species\" keyed by speciesId — this async write is the actual"
            + " staleness risk for a cached getSpecies(id) response")
    void enrichShouldEvictSpeciesCache() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          SpeciesEnrichmentServiceImpl.class.getMethod(
              "enrich", Long.class, AiModelPreference.class);
      org.springframework.cache.annotation.CacheEvict evict =
          method.getAnnotation(org.springframework.cache.annotation.CacheEvict.class);

      assertThat(evict).isNotNull();
      assertThat(evict.value()).containsExactly("species");
      assertThat(evict.key()).isEqualTo("#speciesId");
    }
  }
}
