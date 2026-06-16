package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.client.VisionAnnotationClient;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.dto.CureAdviceRequest;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.mapper.IdentificationMapper;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.identification.service.impl.IdentificationServiceImpl;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.storage.FileStorageService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdentificationServiceImpl — Unit Tests")
class IdentificationServiceImplTest {

  @Mock private DeepSeekClient deepSeekClient;
  @Mock private GitHubModelsClient gitHubModelsClient;
  @Mock private VisionAnnotationClient visionAnnotationClient;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private IdentificationMapper identificationMapper;
  @Mock private PlantRepository plantRepository;
  @Mock private ReminderRepository reminderRepository;
  @Mock private FileStorageService fileStorageService;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();
  @Mock private UserRepository userRepository;
  @Mock private PlantNetClient plantNetClient;
  @Mock private OllamaClient ollamaClient;

  private IdentificationServiceImpl identificationService;

  private static final Long USER_ID = 1L;
  private static final Long PLANT_ID = 10L;

  @BeforeEach
  void setUp() {
    identificationService =
        new IdentificationServiceImpl(
            deepSeekClient,
            visionAnnotationClient,
            identificationRepository,
            identificationMapper,
            plantRepository,
            reminderRepository,
            fileStorageService,
            objectMapper,
            gitHubModelsClient,
            userRepository,
            plantNetClient,
            ollamaClient);
  }

  private MockMultipartFile validImage() {
    return new MockMultipartFile(
        "images", "plant.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
  }

  private String validIdentificationJson() {
    return """
        {
          "species": "Monstera deliciosa",
          "commonName": "Swiss cheese plant",
          "confidence": "HIGH",
          "healthStatus": "HEALTHY",
          "healthNotes": null,
          "carePlan": {
            "wateringFrequencyDays": 7,
            "fertilizingFrequencyDays": 30,
            "repottingFrequencyMonths": 12,
            "careCards": [
              {
                "type": "WATERING",
                "title": "Watering",
                "icon": "water_drop",
                "summary": "Water every 7 days",
                "detail": "Keep soil moist but not waterlogged.",
                "urgency": "MEDIUM",
                "seasonalVariation": "Water less in winter."
              }
            ],
            "beginnerWarnings": ["Avoid overwatering"]
          }
        }
        """;
  }

  @Nested
  @DisplayName("identify()")
  class Identify {

    @Test
    @DisplayName("should complete happy path: DeepSeek vision → persist → update plant species")
    void shouldCompleteHappyPath() throws Exception {
      List<MultipartFile> images = List.of(validImage());

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      Identification completedEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .photoUrl("/photos/uuid.jpg")
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .confidence(0.9)
              .healthStatus("HEALTHY")
              .status(IdentificationStatus.COMPLETED)
              .build();

      when(identificationRepository.save(any()))
          .thenReturn(pendingEntity)
          .thenReturn(completedEntity);
      when(plantRepository.existsByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(true);
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(
              Optional.of(
                  Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("My plant").build()));

      IdentificationResponse response =
          identificationService.identify(images, List.of("leaf"), PLANT_ID, USER_ID).get();

      assertThat(response.getScientificName()).isEqualTo("Monstera deliciosa");
      assertThat(response.getCommonName()).isEqualTo("Swiss cheese plant");
      assertThat(response.getConfidence()).isEqualTo(0.9);
      assertThat(response.getHealthStatus()).isEqualTo("HEALTHY");
      assertThat(response.getStatus()).isEqualTo(IdentificationStatus.COMPLETED);
      assertThat(response.getTopResults()).isEmpty();
      assertThat(response.getCarePlan()).isNotNull();
      assertThat(response.getCarePlan().getWateringFrequencyDays()).isEqualTo(7);

      verify(fileStorageService).savePhoto(any());
      verify(gitHubModelsClient).identifyPlant(any(), any());
      verify(identificationRepository, times(2)).save(any(Identification.class));
      verify(plantRepository).save(any(Plant.class));
    }

    @Test
    @DisplayName("should mark entity FAILED when DeepSeek throws")
    void shouldMarkEntityFailedWhenDeepSeekThrows() {
      List<MultipartFile> images = List.of(validImage());

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any()))
          .thenReturn(pendingEntity)
          .thenReturn(pendingEntity);
      when(gitHubModelsClient.identifyPlant(any(), any()))
          .thenThrow(new PlantPalException("Plant identification service unavailable", 503));

      assertThatThrownBy(() -> identificationService.identify(images, null, null, USER_ID).get())
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("Plant identification service unavailable");

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(IdentificationStatus.FAILED);
    }

    @Test
    @DisplayName("should NOT update plant species when plantId is not owned by user")
    void shouldNotUpdatePlantWhenNotOwned() throws Exception {
      List<MultipartFile> images = List.of(validImage());
      Long foreignPlantId = 99L;

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(foreignPlantId)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .confidence(0.9)
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantRepository.existsByIdAndUserId(foreignPlantId, USER_ID)).thenReturn(false);

      identificationService.identify(images, null, foreignPlantId, USER_ID).get();

      verify(plantRepository, never()).save(any(Plant.class));
      verify(reminderRepository, never()).save(any(Reminder.class));
    }
  }

  @Nested
  @DisplayName("care plan parsing")
  class CarePlanParsing {

    @Test
    @DisplayName("should parse all care plan fields from DeepSeek combined response")
    void shouldParseValidCarePlan() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .confidence(0.9)
              .healthStatus("HEALTHY")
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      CarePlanDto carePlan = response.getCarePlan();
      assertThat(carePlan).isNotNull();
      assertThat(carePlan.getWateringFrequencyDays()).isEqualTo(7);
      assertThat(carePlan.getFertilizingFrequencyDays()).isEqualTo(30);
      assertThat(carePlan.getRepottingFrequencyMonths()).isEqualTo(12);
      assertThat(carePlan.getCareCards()).hasSize(1);
      assertThat(carePlan.getCareCards().get(0).getType()).isEqualTo("WATERING");
      assertThat(carePlan.getBeginnerWarnings()).containsExactly("Avoid overwatering");
    }

    @Test
    @DisplayName("should return fallback care plan when DeepSeek returns malformed JSON")
    void shouldReturnFallbackOnMalformedJson() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn("not valid json {{{");

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getCarePlan()).isNotNull();
      assertThat(response.getCarePlan().getCareCards()).isNotEmpty();
      assertThat(response.getCarePlan().getCareCards().get(0).getType()).isEqualTo("WATERING");
    }

    @Test
    @DisplayName("should return fallback care plan when carePlan field is null in response")
    void shouldReturnFallbackWhenCarePlanIsNull() throws Exception {
      String jsonWithNullCarePlan =
          """
          {
            "species": "Monstera deliciosa",
            "commonName": "Swiss cheese plant",
            "confidence": "MEDIUM",
            "healthStatus": "UNKNOWN",
            "healthNotes": null,
            "carePlan": null
          }
          """;
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(jsonWithNullCarePlan);

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getCarePlan()).isNotNull();
      assertThat(response.getCarePlan().getCareCards()).isNotEmpty();
    }

    @Test
    @DisplayName("fallback care plan always has at least one care card")
    void fallbackCareCardNeverNull() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn("{}");

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getCarePlan().getCareCards()).isNotNull().isNotEmpty();
    }
  }

  @Nested
  @DisplayName("annotation regions")
  class AnnotationRegions {

    private Identification completedEntity() {
      return Identification.builder()
          .id(1L)
          .userId(USER_ID)
          .status(IdentificationStatus.COMPLETED)
          .scientificName("Monstera deliciosa")
          .commonName("Swiss cheese plant")
          .confidence(0.9)
          .build();
    }

    @Test
    @DisplayName("should return empty annotationRegions when annotation JSON is malformed")
    void shouldReturnEmptyRegionsOnMalformedJson() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("not valid json {{{");
      when(identificationRepository.save(any())).thenReturn(completedEntity());

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getAnnotationRegions()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("should populate annotationRegions with polygon points from valid JSON")
    void shouldPopulateRegionsOnValidPolygonJson() throws Exception {
      String annotationJson =
          """
          {"regions":[
            {"label":"Monstera deliciosa","type":"PLANT","confidence":"HIGH",
             "polygon":[
               {"xPct":5,"yPct":5},{"xPct":60,"yPct":3},{"xPct":95,"yPct":10},{"xPct":92,"yPct":50},
               {"xPct":95,"yPct":90},{"xPct":50,"yPct":95},{"xPct":5,"yPct":88},{"xPct":3,"yPct":45}
             ]},
            {"label":"Yellowing leaf","type":"DISEASE","confidence":"MEDIUM",
             "polygon":[
               {"xPct":20,"yPct":60},{"xPct":35,"yPct":58},{"xPct":50,"yPct":65},{"xPct":48,"yPct":78},
               {"xPct":30,"yPct":82},{"xPct":18,"yPct":75},{"xPct":15,"yPct":67},{"xPct":18,"yPct":62}
             ]}
          ]}
          """;
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn(annotationJson);
      when(identificationRepository.save(any())).thenReturn(completedEntity());

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getAnnotationRegions()).hasSize(2);
      assertThat(response.getAnnotationRegions().get(0).getType()).isEqualTo("PLANT");
      assertThat(response.getAnnotationRegions().get(0).getConfidence()).isEqualTo("HIGH");
      assertThat(response.getAnnotationRegions().get(0).getPolygon()).hasSize(8);
      assertThat(response.getAnnotationRegions().get(0).getPolygon().get(0).getXPct()).isEqualTo(5);
      assertThat(response.getAnnotationRegions().get(1).getType()).isEqualTo("DISEASE");
      assertThat(response.getAnnotationRegions().get(1).getPolygon()).hasSize(8);
    }

    @Test
    @DisplayName("should accept legacy bounding-box regions (PlantNet fallback path)")
    void shouldAcceptLegacyBoundingBoxRegions() throws Exception {
      String annotationJson =
          """
          {"regions":[
            {"label":"Ficus lyrata","type":"PLANT","confidence":"LOW",
             "boundingBox":{"xPct":0,"yPct":0,"widthPct":100,"heightPct":100}}
          ]}
          """;
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn(annotationJson);
      when(identificationRepository.save(any())).thenReturn(completedEntity());

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getAnnotationRegions()).hasSize(1);
      assertThat(response.getAnnotationRegions().get(0).getPolygon()).isNull();
      assertThat(response.getAnnotationRegions().get(0).getBoundingBox()).isNotNull();
      assertThat(response.getAnnotationRegions().get(0).getBoundingBox().getWidthPct())
          .isEqualTo(100);
    }

    @Test
    @DisplayName("should clear polygon to null when it has fewer than 3 points")
    void shouldClearDegeneratePolygon() throws Exception {
      String annotationJson =
          """
          {"regions":[
            {"label":"Bad region","type":"DISEASE","confidence":"LOW",
             "polygon":[{"xPct":10,"yPct":10},{"xPct":20,"yPct":20}]}
          ]}
          """;
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn(annotationJson);
      when(identificationRepository.save(any())).thenReturn(completedEntity());

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getAnnotationRegions()).hasSize(1);
      assertThat(response.getAnnotationRegions().get(0).getPolygon()).isNull();
    }
  }

  @Nested
  @DisplayName("reminder auto-creation")
  class ReminderCreation {

    private String identificationJsonWithFertilizing() {
      return """
          {
            "species": "Monstera deliciosa",
            "commonName": "Swiss cheese plant",
            "confidence": "HIGH",
            "healthStatus": "HEALTHY",
            "healthNotes": null,
            "carePlan": {
              "wateringFrequencyDays": 7,
              "fertilizingFrequencyDays": 30,
              "repottingFrequencyMonths": 6,
              "careCards": [{"type":"WATERING","title":"W","icon":"water_drop","summary":"s","detail":"d","urgency":"LOW","seasonalVariation":null}],
              "beginnerWarnings": []
            }
          }
          """;
    }

    private String identificationJsonNoFertilizing() {
      return """
          {
            "species": "Monstera deliciosa",
            "commonName": "Swiss cheese plant",
            "confidence": "HIGH",
            "healthStatus": "HEALTHY",
            "healthNotes": null,
            "carePlan": {
              "wateringFrequencyDays": 14,
              "fertilizingFrequencyDays": 0,
              "repottingFrequencyMonths": 24,
              "careCards": [{"type":"WATERING","title":"W","icon":"water_drop","summary":"s","detail":"d","urgency":"LOW","seasonalVariation":null}],
              "beginnerWarnings": []
            }
          }
          """;
    }

    private void stubHappyPath(String identificationJson) {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(identificationJson);

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .confidence(0.9)
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantRepository.existsByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(true);
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(
              Optional.of(Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("p").build()));
      when(reminderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName(
        "should create WATERING, FERTILIZING, and REPOTTING reminders when fertilizingFrequencyDays > 0")
    void shouldCreateThreeRemindersWhenFertilizingEnabled() throws Exception {
      stubHappyPath(identificationJsonWithFertilizing());

      identificationService.identify(List.of(validImage()), null, PLANT_ID, USER_ID).get();

      ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository, times(3)).save(captor.capture());

      List<CareType> careTypes = captor.getAllValues().stream().map(Reminder::getCareType).toList();
      assertThat(careTypes)
          .containsExactlyInAnyOrder(CareType.WATERING, CareType.FERTILIZING, CareType.REPOTTING);
    }

    @Test
    @DisplayName("should skip FERTILIZING reminder when fertilizingFrequencyDays = 0")
    void shouldSkipFertilizingReminderWhenZero() throws Exception {
      stubHappyPath(identificationJsonNoFertilizing());

      identificationService.identify(List.of(validImage()), null, PLANT_ID, USER_ID).get();

      ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository, times(2)).save(captor.capture());

      List<CareType> careTypes = captor.getAllValues().stream().map(Reminder::getCareType).toList();
      assertThat(careTypes).containsExactlyInAnyOrder(CareType.WATERING, CareType.REPOTTING);
      assertThat(careTypes).doesNotContain(CareType.FERTILIZING);
    }

    @Test
    @DisplayName("should set correct frequencyDays on each reminder")
    void shouldSetCorrectFrequencyDays() throws Exception {
      stubHappyPath(identificationJsonWithFertilizing());

      identificationService.identify(List.of(validImage()), null, PLANT_ID, USER_ID).get();

      ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository, atLeastOnce()).save(captor.capture());

      captor.getAllValues().stream()
          .filter(r -> r.getCareType() == CareType.WATERING)
          .findFirst()
          .ifPresent(r -> assertThat(r.getFrequencyDays()).isEqualTo(7));

      captor.getAllValues().stream()
          .filter(r -> r.getCareType() == CareType.FERTILIZING)
          .findFirst()
          .ifPresent(r -> assertThat(r.getFrequencyDays()).isEqualTo(30));

      captor.getAllValues().stream()
          .filter(r -> r.getCareType() == CareType.REPOTTING)
          .findFirst()
          .ifPresent(r -> assertThat(r.getFrequencyDays()).isEqualTo(6 * 30));
    }
  }

  @Nested
  @DisplayName("getCureAdvice()")
  class CureAdvice {

    private Identification ownedIdentification() {
      return Identification.builder()
          .id(1L)
          .userId(USER_ID)
          .scientificName("Monstera deliciosa")
          .build();
    }

    private CureAdviceRequest req() {
      return new CureAdviceRequest("Yellowing leaf — possible overwatering", "Monstera deliciosa");
    }

    @Test
    @DisplayName("should return advice text from DeepSeek on happy path")
    void shouldReturnAdviceOnHappyPath() throws Exception {
      when(identificationRepository.findById(1L))
          .thenReturn(java.util.Optional.of(ownedIdentification()));
      when(deepSeekClient.generateCureAdvice(any(), any()))
          .thenReturn("1. Remove affected leaves. 2. Reduce watering.");

      var response = identificationService.getCureAdvice(1L, req(), USER_ID).get();

      assertThat(response.getAdvice()).isEqualTo("1. Remove affected leaves. 2. Reduce watering.");
      verify(deepSeekClient)
          .generateCureAdvice("Monstera deliciosa", "Yellowing leaf — possible overwatering");
    }

    @Test
    @DisplayName("should throw 429 when cure advice rate limit is exceeded")
    void shouldThrowWhenRateLimited() throws Exception {
      when(identificationRepository.findById(1L))
          .thenReturn(java.util.Optional.of(ownedIdentification()));
      when(deepSeekClient.generateCureAdvice(any(), any())).thenReturn("1. Step one.");

      for (int i = 0; i < 10; i++) {
        identificationService.getCureAdvice(1L, req(), USER_ID).get();
      }

      assertThatThrownBy(() -> identificationService.getCureAdvice(1L, req(), USER_ID).get())
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("rate limit");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when identification is not owned by user")
    void shouldThrowWhenNotOwned() {
      Identification foreignIdentification = Identification.builder().id(1L).userId(99L).build();
      when(identificationRepository.findById(1L))
          .thenReturn(java.util.Optional.of(foreignIdentification));

      assertThatThrownBy(() -> identificationService.getCureAdvice(1L, req(), USER_ID).get())
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should propagate PlantPalException 503 when DeepSeek fails")
    void shouldPropagate503WhenDeepSeekFails() {
      when(identificationRepository.findById(1L))
          .thenReturn(java.util.Optional.of(ownedIdentification()));
      when(deepSeekClient.generateCureAdvice(any(), any()))
          .thenThrow(new PlantPalException("Cure advice unavailable", 503));

      assertThatThrownBy(() -> identificationService.getCureAdvice(1L, req(), USER_ID).get())
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("Cure advice unavailable");
    }
  }
}
