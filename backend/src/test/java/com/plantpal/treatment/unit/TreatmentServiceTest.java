package com.plantpal.treatment.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.dto.TreatmentStepDto;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.dto.TreatmentPlanResponse;
import com.plantpal.reminder.service.TreatmentPlanService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.treatment.dto.CreateTreatmentRequest;
import com.plantpal.treatment.dto.TreatmentResponse;
import com.plantpal.treatment.entity.Treatment;
import com.plantpal.treatment.entity.TreatmentStatus;
import com.plantpal.treatment.repository.TreatmentRepository;
import com.plantpal.treatment.service.impl.TreatmentServiceImpl;
import com.plantpal.user.repository.UserRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("TreatmentService — Unit Tests")
class TreatmentServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long PLANT_ID = 10L;

  @Mock private TreatmentRepository treatmentRepository;
  @Mock private PlantRepository plantRepository;
  @Mock private TreatmentPlanService treatmentPlanService;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private DeepSeekClient deepSeekClient;
  @Mock private OllamaClient ollamaClient;
  @Mock private AnthropicClient anthropicClient;
  @Mock private UserRepository userRepository;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();
  @Mock private com.plantpal.gateway.GatewayClient gatewayClient;

  private TreatmentServiceImpl treatmentService;

  private Plant ownedPlant() {
    return Plant.builder()
        .id(PLANT_ID)
        .userId(USER_ID)
        .nickname("My Monstera")
        .species("Monstera deliciosa")
        .build();
  }

  @BeforeEach
  void setUp() {
    // Runnable::run executes synchronously — deterministic for the fire-and-forget disease
    // description call (no real thread pool needed in unit tests).
    treatmentService =
        new TreatmentServiceImpl(
            treatmentRepository,
            plantRepository,
            treatmentPlanService,
            identificationRepository,
            deepSeekClient,
            ollamaClient,
            anthropicClient,
            userRepository,
            objectMapper,
            Runnable::run,
            gatewayClient,
            new com.plantpal.gateway.GatewayProperties(false, "http://localhost:8085"));
  }

  /** Flips the gateway flag on for a single test. */
  private void enableGateway() {
    org.springframework.test.util.ReflectionTestUtils.setField(
        treatmentService,
        "gatewayProperties",
        new com.plantpal.gateway.GatewayProperties(true, "http://localhost:8085"));
  }

  @Nested
  @DisplayName("createTreatment()")
  class CreateTreatment {

    @Test
    @DisplayName("should create a DRAFT treatment and fire disease description generation")
    void shouldCreateTreatmentSuccessfully() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(Optional.of(ownedPlant()));
      when(treatmentRepository.findByPlantIdAndDiseaseNameAndStatusIn(
              eq(PLANT_ID), eq("Powdery mildew"), any()))
          .thenReturn(List.of());
      when(treatmentRepository.save(any(Treatment.class)))
          .thenAnswer(
              inv -> {
                Treatment t = inv.getArgument(0);
                t.setId(99L);
                return t;
              });
      when(treatmentRepository.findById(99L))
          .thenReturn(Optional.of(Treatment.builder().id(99L).build()));
      when(deepSeekClient.generateDiseaseDescription(anyString(), anyString()))
          .thenReturn("Powdery mildew is a fungal disease...");

      var request =
          CreateTreatmentRequest.builder()
              .plantId(PLANT_ID)
              .identificationId(5L)
              .diseaseName("Powdery mildew")
              .build();

      TreatmentResponse result = treatmentService.createTreatment(request, USER_ID);

      assertThat(result.getId()).isEqualTo(99L);
      assertThat(result.getStatus()).isEqualTo(TreatmentStatus.DRAFT);

      // 2 saves: the initial DRAFT row, then the fire-and-forget disease-description update —
      // the test executor runs that synchronously (Runnable::run), unlike the real aiTaskExecutor.
      ArgumentCaptor<Treatment> captor = ArgumentCaptor.forClass(Treatment.class);
      verify(treatmentRepository, times(2)).save(captor.capture());
      Treatment firstSave = captor.getAllValues().get(0);
      assertThat(firstSave.getStatus()).isEqualTo(TreatmentStatus.DRAFT);
      assertThat(firstSave.getDiseaseName()).isEqualTo("Powdery mildew");

      verify(deepSeekClient).generateDiseaseDescription("Monstera deliciosa", "Powdery mildew");
    }

    @Test
    @DisplayName("should reject when an active treatment already exists for plant+disease")
    void shouldRejectWhenActiveTreatmentExists() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(Optional.of(ownedPlant()));
      when(treatmentRepository.findByPlantIdAndDiseaseNameAndStatusIn(
              eq(PLANT_ID), eq("Powdery mildew"), any()))
          .thenReturn(
              List.of(Treatment.builder().id(1L).status(TreatmentStatus.IN_PROGRESS).build()));

      var request =
          CreateTreatmentRequest.builder()
              .plantId(PLANT_ID)
              .identificationId(5L)
              .diseaseName("Powdery mildew")
              .build();

      assertThatThrownBy(() -> treatmentService.createTreatment(request, USER_ID))
          .isInstanceOf(ValidationException.class);

      verify(treatmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when the plant is not owned by the user")
    void shouldThrowWhenPlantNotOwned() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.empty());

      var request =
          CreateTreatmentRequest.builder()
              .plantId(PLANT_ID)
              .identificationId(5L)
              .diseaseName("Powdery mildew")
              .build();

      assertThatThrownBy(() -> treatmentService.createTreatment(request, USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(treatmentRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("craftPlan()")
  class CraftPlan {

    private Treatment draftTreatment() {
      return Treatment.builder()
          .id(7L)
          .plantId(PLANT_ID)
          .userId(USER_ID)
          .diseaseName("Powdery mildew")
          .status(TreatmentStatus.DRAFT)
          .build();
    }

    @Test
    @DisplayName(
        "should delegate to TreatmentPlanService.createFromActionPlan and move to IN_PROGRESS")
    void shouldCraftPlanSuccessfully() throws Exception {
      when(treatmentRepository.findByIdAndUserId(7L, USER_ID))
          .thenReturn(Optional.of(draftTreatment()));
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(Optional.of(ownedPlant()));

      String aiJson =
          """
          {"advice":"ignored","actionPlan":{"type":"TREATMENT","steps":[
            {"order":1,"instruction":"Remove affected leaves","dueOffsetDays":0}
          ]}}
          """;
      when(deepSeekClient.generateCureAdvice("Monstera deliciosa", "Powdery mildew"))
          .thenReturn(aiJson);
      when(treatmentPlanService.createFromActionPlan(
              eq(PLANT_ID), eq(USER_ID), eq("Powdery mildew"), eq("PEST"), any()))
          .thenReturn(TreatmentPlanResponse.builder().id(500L).plantId(PLANT_ID).build());
      when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));

      TreatmentResponse result = treatmentService.craftPlan(7L, USER_ID).get();

      assertThat(result.getStatus()).isEqualTo(TreatmentStatus.IN_PROGRESS);
      assertThat(result.getTreatmentPlanId()).isEqualTo(500L);

      ArgumentCaptor<ActionPlanDto> planCaptor = ArgumentCaptor.forClass(ActionPlanDto.class);
      verify(treatmentPlanService)
          .createFromActionPlan(
              eq(PLANT_ID), eq(USER_ID), eq("Powdery mildew"), eq("PEST"), planCaptor.capture());
      List<TreatmentStepDto> steps = planCaptor.getValue().getSteps();
      assertThat(steps).hasSize(1);
      assertThat(steps.get(0).getInstruction()).isEqualTo("Remove affected leaves");

      ArgumentCaptor<Treatment> captor = ArgumentCaptor.forClass(Treatment.class);
      verify(treatmentRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(TreatmentStatus.IN_PROGRESS);
      assertThat(captor.getValue().getStartedAt()).isNotNull();

      ArgumentCaptor<Plant> plantCaptor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(plantCaptor.capture());
      assertThat(plantCaptor.getValue().getActiveTreatmentId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("should reject crafting a plan when treatment is not DRAFT")
    void shouldRejectWhenNotDraft() {
      Treatment inProgress = draftTreatment();
      inProgress.setStatus(TreatmentStatus.IN_PROGRESS);
      when(treatmentRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(inProgress));

      // No Spring AOP proxy in a unit test — @Async has no effect, so craftPlan() throws
      // synchronously here rather than completing the future exceptionally.
      assertThatThrownBy(() -> treatmentService.craftPlan(7L, USER_ID))
          .isInstanceOf(ValidationException.class);

      verify(treatmentPlanService, never()).createFromActionPlan(any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("gateway routing (D022 gateway swap, flag on) — Chunk 3")
  class GatewayRouting {

    private Treatment draftTreatment() {
      return Treatment.builder()
          .id(7L)
          .plantId(PLANT_ID)
          .userId(USER_ID)
          .diseaseName("Powdery mildew")
          .status(TreatmentStatus.DRAFT)
          .build();
    }

    private io.platform.contracts.aigateway.AiResponse gatewayResponse(String result) {
      io.platform.contracts.aigateway.AiResponse response =
          new io.platform.contracts.aigateway.AiResponse();
      response.setResult(result);
      response.setModel("stub-model");
      response.setProvider("stub-provider");
      response.setTokensIn(1);
      response.setTokensOut(1);
      response.setComputedCost(java.math.BigDecimal.ZERO);
      return response;
    }

    @Test
    @DisplayName("craftPlan() should route ANTHROPIC_CLAUDE cure advice through GatewayClient")
    void craftPlanShouldRouteAnthropicThroughGateway() throws Exception {
      enableGateway();
      when(anthropicClient.getDefaultModel()).thenReturn("claude-sonnet-4-6");
      when(treatmentRepository.findByIdAndUserId(7L, USER_ID))
          .thenReturn(Optional.of(draftTreatment()));
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(Optional.of(ownedPlant()));
      when(userRepository.findById(USER_ID))
          .thenReturn(
              Optional.of(
                  com.plantpal.user.entity.User.builder()
                      .id(USER_ID)
                      .reasoningModelPreference(
                          com.plantpal.user.entity.ReasoningModelPreference.ANTHROPIC_CLAUDE)
                      .build()));
      String aiJson =
          """
          {"advice":"ignored","actionPlan":{"type":"TREATMENT","steps":[
            {"order":1,"instruction":"Remove affected leaves","dueOffsetDays":0}
          ]}}
          """;
      when(gatewayClient.request(any())).thenReturn(gatewayResponse(aiJson));
      when(treatmentPlanService.createFromActionPlan(
              eq(PLANT_ID), eq(USER_ID), eq("Powdery mildew"), eq("PEST"), any()))
          .thenReturn(TreatmentPlanResponse.builder().id(500L).plantId(PLANT_ID).build());
      when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));

      TreatmentResponse result = treatmentService.craftPlan(7L, USER_ID).get();

      assertThat(result.getStatus()).isEqualTo(TreatmentStatus.IN_PROGRESS);
      verify(anthropicClient, never()).generateCureAdvice(any(), any());
      ArgumentCaptor<io.platform.contracts.aigateway.AiRequest> captor =
          ArgumentCaptor.forClass(io.platform.contracts.aigateway.AiRequest.class);
      verify(gatewayClient).request(captor.capture());
      assertThat(captor.getValue().getAppId()).isEqualTo("plantpal");
      assertThat(captor.getValue().getModelHint()).isEqualTo("claude-sonnet-4-6");
      assertThat(captor.getValue().getContext())
          .containsEntry("systemPrompt", DeepSeekClient.CURE_ADVICE_SYSTEM_PROMPT);
    }

    @Test
    @DisplayName(
        "createTreatment() disease description should route DEEPSEEK_R1 through GatewayClient")
    void createTreatmentShouldRouteDeepSeekDiseaseDescriptionThroughGateway() {
      enableGateway();
      when(deepSeekClient.getModel()).thenReturn("DeepSeek-R1");
      // No stubbed userRepository row → loadReasoningPreference() falls back to DEEPSEEK_R1.
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(Optional.of(ownedPlant()));
      when(treatmentRepository.findByPlantIdAndDiseaseNameAndStatusIn(
              eq(PLANT_ID), eq("Powdery mildew"), any()))
          .thenReturn(List.of());
      when(treatmentRepository.save(any(Treatment.class)))
          .thenAnswer(
              inv -> {
                Treatment t = inv.getArgument(0);
                t.setId(99L);
                return t;
              });
      when(treatmentRepository.findById(99L))
          .thenReturn(Optional.of(Treatment.builder().id(99L).build()));
      when(gatewayClient.request(any()))
          .thenReturn(gatewayResponse("Powdery mildew is a fungal disease..."));

      var request =
          CreateTreatmentRequest.builder()
              .plantId(PLANT_ID)
              .identificationId(5L)
              .diseaseName("Powdery mildew")
              .build();

      treatmentService.createTreatment(request, USER_ID);

      verify(deepSeekClient, never()).generateDiseaseDescription(any(), any());
      ArgumentCaptor<io.platform.contracts.aigateway.AiRequest> captor =
          ArgumentCaptor.forClass(io.platform.contracts.aigateway.AiRequest.class);
      verify(gatewayClient).request(captor.capture());
      assertThat(captor.getValue().getModelHint()).isEqualTo("DeepSeek-R1");
      assertThat(captor.getValue().getContext())
          .containsEntry("systemPrompt", DeepSeekClient.DISEASE_DESCRIPTION_SYSTEM_PROMPT);
    }

    @Test
    @DisplayName(
        "createTreatment() disease description should route OLLAMA_GEMMA3 through GatewayClient"
            + " with configured Ollama model")
    void createTreatmentShouldRouteOllamaDiseaseDescriptionThroughGateway() {
      enableGateway();
      when(ollamaClient.getModel()).thenReturn("gemma3:4b");
      when(userRepository.findById(USER_ID))
          .thenReturn(
              Optional.of(
                  com.plantpal.user.entity.User.builder()
                      .id(USER_ID)
                      .reasoningModelPreference(
                          com.plantpal.user.entity.ReasoningModelPreference.OLLAMA_GEMMA3)
                      .build()));
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(Optional.of(ownedPlant()));
      when(treatmentRepository.findByPlantIdAndDiseaseNameAndStatusIn(
              eq(PLANT_ID), eq("Powdery mildew"), any()))
          .thenReturn(List.of());
      when(treatmentRepository.save(any(Treatment.class)))
          .thenAnswer(
              inv -> {
                Treatment t = inv.getArgument(0);
                t.setId(99L);
                return t;
              });
      when(treatmentRepository.findById(99L))
          .thenReturn(Optional.of(Treatment.builder().id(99L).build()));
      when(gatewayClient.request(any()))
          .thenReturn(gatewayResponse("Powdery mildew is a fungal disease..."));

      var request =
          CreateTreatmentRequest.builder()
              .plantId(PLANT_ID)
              .identificationId(5L)
              .diseaseName("Powdery mildew")
              .build();

      treatmentService.createTreatment(request, USER_ID);

      verify(ollamaClient, never()).generateDiseaseDescription(any(), any());
      ArgumentCaptor<io.platform.contracts.aigateway.AiRequest> captor =
          ArgumentCaptor.forClass(io.platform.contracts.aigateway.AiRequest.class);
      verify(gatewayClient).request(captor.capture());
      assertThat(captor.getValue().getModelHint()).isEqualTo("gemma3:4b");
    }
  }

  @Nested
  @DisplayName("completeTreatment()")
  class CompleteTreatment {

    @Test
    @DisplayName("should mark an IN_PROGRESS treatment COMPLETED with a completedAt timestamp")
    void shouldCompleteSuccessfully() {
      Treatment inProgress =
          Treatment.builder()
              .id(7L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .diseaseName("Powdery mildew")
              .status(TreatmentStatus.IN_PROGRESS)
              .build();
      when(treatmentRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(inProgress));
      when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.empty());

      TreatmentResponse result = treatmentService.completeTreatment(7L, USER_ID);

      assertThat(result.getStatus()).isEqualTo(TreatmentStatus.COMPLETED);
      assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("should clear plant.activeTreatmentId when it points at the completed treatment")
    void shouldClearActiveTreatmentIdOnPlant() {
      Treatment inProgress =
          Treatment.builder()
              .id(7L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .diseaseName("Powdery mildew")
              .status(TreatmentStatus.IN_PROGRESS)
              .build();
      when(treatmentRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(inProgress));
      when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));

      Plant plant = Plant.builder().id(PLANT_ID).userId(USER_ID).activeTreatmentId(7L).build();
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant));

      treatmentService.completeTreatment(7L, USER_ID);

      ArgumentCaptor<Plant> plantCaptor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(plantCaptor.capture());
      assertThat(plantCaptor.getValue().getActiveTreatmentId()).isNull();
    }

    @Test
    @DisplayName("should NOT touch plant.activeTreatmentId when it points at a different treatment")
    void shouldNotClearActiveTreatmentIdWhenItBelongsToAnotherTreatment() {
      Treatment inProgress =
          Treatment.builder()
              .id(7L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .diseaseName("Powdery mildew")
              .status(TreatmentStatus.IN_PROGRESS)
              .build();
      when(treatmentRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(inProgress));
      when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));

      Plant plant = Plant.builder().id(PLANT_ID).userId(USER_ID).activeTreatmentId(999L).build();
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant));

      treatmentService.completeTreatment(7L, USER_ID);

      verify(plantRepository, never()).save(any(Plant.class));
    }

    @Test
    @DisplayName("should reject completing a treatment that is not IN_PROGRESS")
    void shouldRejectWhenNotInProgress() {
      Treatment draft =
          Treatment.builder().id(7L).userId(USER_ID).status(TreatmentStatus.DRAFT).build();
      when(treatmentRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(draft));

      assertThatThrownBy(() -> treatmentService.completeTreatment(7L, USER_ID))
          .isInstanceOf(ValidationException.class);

      verify(treatmentRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("syncFromTreatmentPlanCompletion()")
  class SyncFromTreatmentPlanCompletion {

    @Test
    @DisplayName(
        "should complete the wrapping IN_PROGRESS treatment and clear plant.activeTreatmentId")
    void shouldCompleteWrappingTreatment() {
      Treatment inProgress =
          Treatment.builder()
              .id(7L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .diseaseName("Powdery mildew")
              .status(TreatmentStatus.IN_PROGRESS)
              .treatmentPlanId(500L)
              .build();
      when(treatmentRepository.findByTreatmentPlanId(500L)).thenReturn(List.of(inProgress));
      when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));

      Plant plant = Plant.builder().id(PLANT_ID).userId(USER_ID).activeTreatmentId(7L).build();
      when(plantRepository.findById(PLANT_ID)).thenReturn(Optional.of(plant));

      treatmentService.syncFromTreatmentPlanCompletion(500L);

      ArgumentCaptor<Treatment> treatmentCaptor = ArgumentCaptor.forClass(Treatment.class);
      verify(treatmentRepository).save(treatmentCaptor.capture());
      assertThat(treatmentCaptor.getValue().getStatus()).isEqualTo(TreatmentStatus.COMPLETED);
      assertThat(treatmentCaptor.getValue().getCompletedAt()).isNotNull();

      ArgumentCaptor<Plant> plantCaptor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(plantCaptor.capture());
      assertThat(plantCaptor.getValue().getActiveTreatmentId()).isNull();
    }

    @Test
    @DisplayName("should no-op when no Treatment wraps this TreatmentPlan")
    void shouldNoOpWhenNoTreatmentWrapsPlan() {
      when(treatmentRepository.findByTreatmentPlanId(500L)).thenReturn(List.of());

      treatmentService.syncFromTreatmentPlanCompletion(500L);

      verify(treatmentRepository, never()).save(any());
      verify(plantRepository, never()).save(any());
    }

    @Test
    @DisplayName("should no-op when the wrapping treatment is not IN_PROGRESS")
    void shouldNoOpWhenTreatmentNotInProgress() {
      Treatment alreadyDone =
          Treatment.builder()
              .id(7L)
              .plantId(PLANT_ID)
              .userId(USER_ID)
              .status(TreatmentStatus.COMPLETED)
              .treatmentPlanId(500L)
              .build();
      when(treatmentRepository.findByTreatmentPlanId(500L)).thenReturn(List.of(alreadyDone));

      treatmentService.syncFromTreatmentPlanCompletion(500L);

      verify(treatmentRepository, never()).save(any());
      verify(plantRepository, never()).findById(any());
    }
  }
}
