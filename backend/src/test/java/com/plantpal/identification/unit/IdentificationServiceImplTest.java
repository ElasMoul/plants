package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.client.PlantNetDiseaseClient;
import com.plantpal.identification.client.VisionAnnotationClient;
import com.plantpal.identification.config.KafkaTopicConfig;
import com.plantpal.identification.dto.AddCareCardRequest;
import com.plantpal.identification.dto.AnnotationRegionDto;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.dto.CureAdviceRequest;
import com.plantpal.identification.dto.IdentificationPendingResponse;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.dto.PlantMatchDto;
import com.plantpal.identification.dto.ResolvePlantRequest;
import com.plantpal.identification.dto.ResolveSpeciesRequest;
import com.plantpal.identification.dto.SpeciesMatchDto;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.event.IdentificationRequestedEvent;
import com.plantpal.identification.mapper.IdentificationMapper;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.identification.service.impl.IdentificationServiceImpl;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.storage.FileStorageService;
import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.repository.UserRepository;
import java.time.Instant;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
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
  @Mock private PlantNetDiseaseClient plantNetDiseaseClient;
  @Mock private OllamaClient ollamaClient;
  @Mock private AnthropicClient anthropicClient;
  @Mock private KafkaTemplate<String, Object> kafkaTemplate;
  @Mock private org.springframework.cache.CacheManager cacheManager;
  @Mock private org.springframework.cache.Cache plantsCache;
  @Mock private com.plantpal.species.repository.SpeciesRepository speciesRepository;
  @Mock private com.plantpal.species.service.SpeciesService speciesService;
  @Mock private com.plantpal.plant.service.PlantService plantService;
  @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

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
            plantNetDiseaseClient,
            ollamaClient,
            anthropicClient,
            kafkaTemplate,
            cacheManager,
            speciesRepository,
            speciesService,
            plantService,
            eventPublisher,
            Runnable::run);
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

  /** Submits the identification request and captures the event published to Kafka. */
  private IdentificationRequestedEvent submitAndCaptureEvent(
      List<MultipartFile> images, List<String> organs, Long plantId, Long userId) throws Exception {
    identificationService.submitIdentification(images, plantId, null, userId, organs).get();
    ArgumentCaptor<IdentificationRequestedEvent> captor =
        ArgumentCaptor.forClass(IdentificationRequestedEvent.class);
    verify(kafkaTemplate)
        .send(eq(KafkaTopicConfig.IDENTIFICATION_REQUESTED_TOPIC), captor.capture());
    return captor.getValue();
  }

  private byte[] testJpegBytes(int width, int height) throws Exception {
    var image =
        new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "JPEG", out);
    return out.toByteArray();
  }

  private List<AnnotationRegionDto> parseRegions(String json) throws Exception {
    var root = objectMapper.readTree(json);
    var regions = root.get("regions");
    List<AnnotationRegionDto> parsed =
        objectMapper.convertValue(
            regions,
            objectMapper
                .getTypeFactory()
                .constructCollectionType(List.class, AnnotationRegionDto.class));
    parsed.forEach(
        r -> {
          if (r.getPolygon() != null && r.getPolygon().size() < 3) {
            r.setPolygon(null);
          }
        });
    return parsed;
  }

  @Nested
  @DisplayName("submitIdentification() + processIdentification()")
  class Identify {

    @Test
    @DisplayName("should complete happy path: GitHubModels vision → persist → update plant species")
    void shouldCompleteHappyPath() throws Exception {
      List<MultipartFile> images = List.of(validImage());

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();

      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(plantRepository.existsByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(true);
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(
              Optional.of(
                  Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("My plant").build()));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(images, List.of("leaf"), PLANT_ID, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      Identification saved = captor.getAllValues().get(1);

      assertThat(saved.getScientificName()).isEqualTo("Monstera deliciosa");
      assertThat(saved.getCommonName()).isEqualTo("Swiss cheese plant");
      assertThat(saved.getConfidence()).isEqualTo(0.9);
      assertThat(saved.getHealthStatus()).isEqualTo("HEALTHY");
      assertThat(saved.getStatus()).isEqualTo(IdentificationStatus.COMPLETED);

      verify(fileStorageService).savePhoto(any());
      verify(gitHubModelsClient).identifyPlant(any(), any());
      verify(plantRepository).save(any(Plant.class));
    }

    @Test
    @DisplayName("should mark entity FAILED when identification client throws")
    void shouldMarkEntityFailedWhenIdentificationThrows() throws Exception {
      List<MultipartFile> images = List.of(validImage());

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(gitHubModelsClient.identifyPlant(any(), any()))
          .thenThrow(new PlantPalException("Plant identification service unavailable", 503));

      IdentificationRequestedEvent event = submitAndCaptureEvent(images, null, null, USER_ID);
      identificationService.processIdentification(event);

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
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(foreignPlantId)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(plantRepository.existsByIdAndUserId(foreignPlantId, USER_ID)).thenReturn(false);

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(images, null, foreignPlantId, USER_ID);
      identificationService.processIdentification(event);

      verify(plantRepository, never()).save(any(Plant.class));
      verify(reminderRepository, never()).save(any(Reminder.class));
    }
  }

  @Nested
  @DisplayName("care plan parsing")
  class CarePlanParsing {

    @Test
    @DisplayName("should parse all care plan fields from combined identification response")
    void shouldParseValidCarePlan() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      CarePlanDto carePlan =
          objectMapper.readValue(captor.getAllValues().get(1).getCarePlan(), CarePlanDto.class);

      assertThat(carePlan).isNotNull();
      assertThat(carePlan.getWateringFrequencyDays()).isEqualTo(7);
      assertThat(carePlan.getFertilizingFrequencyDays()).isEqualTo(30);
      assertThat(carePlan.getRepottingFrequencyMonths()).isEqualTo(12);
      assertThat(carePlan.getCareCards()).hasSize(1);
      assertThat(carePlan.getCareCards().get(0).getType()).isEqualTo("WATERING");
      assertThat(carePlan.getBeginnerWarnings()).containsExactly("Avoid overwatering");
    }

    @Test
    @DisplayName("should return fallback care plan when identification response is malformed JSON")
    void shouldReturnFallbackOnMalformedJson() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn("not valid json {{{");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      CarePlanDto carePlan =
          objectMapper.readValue(captor.getAllValues().get(1).getCarePlan(), CarePlanDto.class);

      assertThat(carePlan).isNotNull();
      assertThat(carePlan.getCareCards()).isNotEmpty();
      assertThat(carePlan.getCareCards().get(0).getType()).isEqualTo("WATERING");
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
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(jsonWithNullCarePlan);

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      CarePlanDto carePlan =
          objectMapper.readValue(captor.getAllValues().get(1).getCarePlan(), CarePlanDto.class);

      assertThat(carePlan).isNotNull();
      assertThat(carePlan.getCareCards()).isNotEmpty();
    }

    @Test
    @DisplayName("fallback care plan always has at least one care card")
    void fallbackCareCardNeverNull() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn("{}");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      CarePlanDto carePlan =
          objectMapper.readValue(captor.getAllValues().get(1).getCarePlan(), CarePlanDto.class);

      assertThat(carePlan.getCareCards()).isNotNull().isNotEmpty();
    }
  }

  @Nested
  @DisplayName("annotation regions")
  class AnnotationRegions {

    private Identification pendingEntity() {
      return Identification.builder()
          .id(1L)
          .userId(USER_ID)
          .status(IdentificationStatus.PENDING)
          .build();
    }

    @Test
    @DisplayName("should return empty annotationRegions when annotation JSON is malformed")
    void shouldReturnEmptyRegionsOnMalformedJson() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("not valid json {{{");
      when(identificationRepository.save(any())).thenReturn(pendingEntity());
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity()));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      assertThat(captor.getAllValues().get(1).getAnnotationRegions())
          .isEqualTo("not valid json {{{");
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
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn(annotationJson);
      when(identificationRepository.save(any())).thenReturn(pendingEntity());
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity()));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      List<AnnotationRegionDto> regions =
          parseRegions(captor.getAllValues().get(1).getAnnotationRegions());

      assertThat(regions).hasSize(2);
      assertThat(regions.get(0).getType()).isEqualTo("PLANT");
      assertThat(regions.get(0).getConfidence()).isEqualTo("HIGH");
      assertThat(regions.get(0).getPolygon()).hasSize(8);
      assertThat(regions.get(0).getPolygon().get(0).getXPct()).isEqualTo(5);
      assertThat(regions.get(1).getType()).isEqualTo("DISEASE");
      assertThat(regions.get(1).getPolygon()).hasSize(8);
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
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn(annotationJson);
      when(identificationRepository.save(any())).thenReturn(pendingEntity());
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity()));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      List<AnnotationRegionDto> regions =
          parseRegions(captor.getAllValues().get(1).getAnnotationRegions());

      assertThat(regions).hasSize(1);
      assertThat(regions.get(0).getPolygon()).isNull();
      assertThat(regions.get(0).getBoundingBox()).isNotNull();
      assertThat(regions.get(0).getBoundingBox().getWidthPct()).isEqualTo(100);
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
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn(annotationJson);
      when(identificationRepository.save(any())).thenReturn(pendingEntity());
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity()));

      IdentificationRequestedEvent event =
          submitAndCaptureEvent(List.of(validImage()), null, null, USER_ID);
      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      List<AnnotationRegionDto> regions =
          parseRegions(captor.getAllValues().get(1).getAnnotationRegions());

      assertThat(regions).hasSize(1);
      assertThat(regions.get(0).getPolygon()).isNull();
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

    private IdentificationRequestedEvent stubHappyPath(String identificationJson) throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(identificationJson);

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(plantRepository.existsByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(true);
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(
              Optional.of(Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("p").build()));
      when(reminderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      return submitAndCaptureEvent(List.of(validImage()), null, PLANT_ID, USER_ID);
    }

    @Test
    @DisplayName(
        "should create WATERING, FERTILIZING, and REPOTTING reminders when fertilizingFrequencyDays > 0")
    void shouldCreateThreeRemindersWhenFertilizingEnabled() throws Exception {
      IdentificationRequestedEvent event = stubHappyPath(identificationJsonWithFertilizing());
      identificationService.processIdentification(event);

      ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository, times(3)).save(captor.capture());

      List<CareType> careTypes = captor.getAllValues().stream().map(Reminder::getCareType).toList();
      assertThat(careTypes)
          .containsExactlyInAnyOrder(CareType.WATERING, CareType.FERTILIZING, CareType.REPOTTING);
    }

    @Test
    @DisplayName("should skip FERTILIZING reminder when fertilizingFrequencyDays = 0")
    void shouldSkipFertilizingReminderWhenZero() throws Exception {
      IdentificationRequestedEvent event = stubHappyPath(identificationJsonNoFertilizing());
      identificationService.processIdentification(event);

      ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
      verify(reminderRepository, times(2)).save(captor.capture());

      List<CareType> careTypes = captor.getAllValues().stream().map(Reminder::getCareType).toList();
      assertThat(careTypes).containsExactlyInAnyOrder(CareType.WATERING, CareType.REPOTTING);
      assertThat(careTypes).doesNotContain(CareType.FERTILIZING);
    }

    @Test
    @DisplayName("should set correct frequencyDays on each reminder")
    void shouldSetCorrectFrequencyDays() throws Exception {
      IdentificationRequestedEvent event = stubHappyPath(identificationJsonWithFertilizing());
      identificationService.processIdentification(event);

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
  @DisplayName("Kafka async pipeline")
  class Kafka {

    @Test
    @DisplayName(
        "submitIdentification: persists Identification as PENDING and publishes requested event")
    void shouldPersistPendingAndPublishEvent() throws Exception {
      List<MultipartFile> images = List.of(validImage());
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationPendingResponse response =
          identificationService
              .submitIdentification(images, PLANT_ID, null, USER_ID, List.of("leaf"))
              .get();

      assertThat(response.getIdentificationId()).isEqualTo(1L);
      assertThat(response.getStatus()).isEqualTo("PENDING");

      ArgumentCaptor<Identification> entityCaptor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository).save(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getStatus()).isEqualTo(IdentificationStatus.PENDING);

      ArgumentCaptor<IdentificationRequestedEvent> eventCaptor =
          ArgumentCaptor.forClass(IdentificationRequestedEvent.class);
      verify(kafkaTemplate)
          .send(eq(KafkaTopicConfig.IDENTIFICATION_REQUESTED_TOPIC), eventCaptor.capture());
      assertThat(eventCaptor.getValue().getIdentificationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("submitIdentification: throws 429 and does not publish when rate limit exceeded")
    void shouldThrowAndSkipPublishWhenRateLimited() {
      List<MultipartFile> images = List.of(validImage());
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      for (int i = 0; i < 20; i++) {
        identificationService.submitIdentification(images, null, null, USER_ID, null);
      }

      assertThatThrownBy(
              () -> identificationService.submitIdentification(images, null, null, USER_ID, null))
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("rate limit");

      verify(kafkaTemplate, times(20))
          .send(eq(KafkaTopicConfig.IDENTIFICATION_REQUESTED_TOPIC), any());
    }

    @Test
    @DisplayName(
        "processIdentification happy path: marks entity COMPLETED and saves annotation regions")
    void shouldCompleteAndSaveAnnotations() throws Exception {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any()))
          .thenReturn(
              "{\"regions\":[{\"label\":\"Monstera\",\"type\":\"PLANT\",\"confidence\":\"HIGH\"}]}");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("DEEPSEEK")
              .requestedAt(Instant.now())
              .build();

      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository).save(captor.capture());
      Identification saved = captor.getValue();
      assertThat(saved.getStatus()).isEqualTo(IdentificationStatus.COMPLETED);
      assertThat(saved.getAnnotationRegions()).contains("Monstera");

      verify(kafkaTemplate).send(eq(KafkaTopicConfig.IDENTIFICATION_COMPLETED_TOPIC), any());
    }

    @Test
    @DisplayName("processIdentification: persists aiModelUsed matching the requested preference")
    void shouldPersistAiModelUsed() throws Exception {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("{\"regions\":[]}");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("GITHUB_GPT4O")
              .requestedAt(Instant.now())
              .build();

      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository).save(captor.capture());
      assertThat(captor.getValue().getAiModelUsed()).isEqualTo("GITHUB_GPT4O");
    }

    @Test
    @DisplayName(
        "processIdentification: OLLAMA_LLAVA failure marks FAILED — no silent fallback to"
            + " GITHUB_GPT4O")
    void shouldMarkFailedWithoutFallbackWhenOllamaFails() throws Exception {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(ollamaClient.identifyPlant(any(), any()))
          .thenThrow(new PlantPalException("Ollama unavailable", 503));
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("{\"regions\":[]}");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("OLLAMA_LLAVA")
              .requestedAt(Instant.now())
              .build();

      identificationService.processIdentification(event);

      verify(gitHubModelsClient, never()).identifyPlant(any(), any());
      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(IdentificationStatus.FAILED);
    }

    @Test
    @DisplayName(
        "processIdentification AI failure: marks entity FAILED without propagating exception")
    void shouldMarkFailedWithoutThrowing() {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any()))
          .thenThrow(new PlantPalException("Plant identification service unavailable", 503));

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("DEEPSEEK")
              .requestedAt(Instant.now())
              .build();

      assertThatCode(() -> identificationService.processIdentification(event))
          .doesNotThrowAnyException();

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(IdentificationStatus.FAILED);

      verify(kafkaTemplate).send(eq(KafkaTopicConfig.IDENTIFICATION_COMPLETED_TOPIC), any());
    }

    @Test
    @DisplayName("processIdentification: stores source image dimensions read from the photo")
    void shouldStoreSourceImageDimensions() throws Exception {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(testJpegBytes(100, 75));
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("{\"regions\":[]}");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("DEEPSEEK")
              .requestedAt(Instant.now())
              .build();

      identificationService.processIdentification(event);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository).save(captor.capture());
      Identification saved = captor.getValue();
      assertThat(saved.getSourceImageWidth()).isEqualTo(100);
      assertThat(saved.getSourceImageHeight()).isEqualTo(75);
    }

    @Test
    @DisplayName("processIdentification COMPLETED path evicts the plants cache")
    void shouldEvictPlantsCacheOnCompleted() throws Exception {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("{\"regions\":[]}");
      when(cacheManager.getCache("plants")).thenReturn(plantsCache);

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("DEEPSEEK")
              .requestedAt(Instant.now())
              .build();

      identificationService.processIdentification(event);

      verify(cacheManager).getCache("plants");
      verify(plantsCache).clear();
    }

    @Test
    @DisplayName("processIdentification FAILED path does not evict the plants cache")
    void shouldNotEvictPlantsCacheOnFailure() {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any()))
          .thenThrow(new PlantPalException("Plant identification service unavailable", 503));

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("DEEPSEEK")
              .requestedAt(Instant.now())
              .build();

      identificationService.processIdentification(event);

      verify(cacheManager, never()).getCache(any());
      verify(plantsCache, never()).clear();
    }

    @Test
    @DisplayName(
        "processIdentification COMPLETED with non-null plantId: updates plant.lastScanId to the"
            + " new identification's id")
    void shouldUpdatePlantLastScanIdWhenLinkedToPlant() throws Exception {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("{\"regions\":[]}");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);
      when(plantRepository.existsByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(true);

      Plant plant = Plant.builder().id(PLANT_ID).userId(USER_ID).build();
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(plant));

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("DEEPSEEK")
              .requestedAt(Instant.now())
              .build();

      identificationService.processIdentification(event);

      ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(captor.capture());
      assertThat(captor.getValue().getLastScanId()).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "processIdentification with null plantId (species-level scan): no plant lookup, no"
            + " exception")
    void shouldSkipPlantLookupWhenPlantIdNull() throws Exception {
      when(fileStorageService.loadPhotoBytes(any())).thenReturn(new byte[] {1, 2, 3});
      when(gitHubModelsClient.identifyPlant(any(), any())).thenReturn(validIdentificationJson());
      when(visionAnnotationClient.analyzeRegions(any(), any())).thenReturn("{\"regions\":[]}");

      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(null)
              .photoUrl("/photos/uuid.jpg")
              .status(IdentificationStatus.PENDING)
              .build();
      when(identificationRepository.findById(1L)).thenReturn(Optional.of(pendingEntity));
      when(identificationRepository.save(any())).thenReturn(pendingEntity);

      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(USER_ID)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("DEEPSEEK")
              .requestedAt(Instant.now())
              .build();

      assertThatCode(() -> identificationService.processIdentification(event))
          .doesNotThrowAnyException();

      verify(plantRepository, never()).findByIdAndUserId(any(), any());
      verify(plantRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("getUserIdentifications()")
  class GetUserIdentifications {

    @Test
    @DisplayName(
        "should return mapped page sorted by createdAt desc with carePlan and annotationRegions parsed")
    void shouldReturnMappedPageWithParsedFields() throws Exception {
      String carePlanJson =
          """
          {"wateringFrequencyDays":7,"fertilizingFrequencyDays":0,"repottingFrequencyMonths":12,
           "careCards":[{"type":"WATERING","title":"Watering","icon":"water_drop","summary":"s",
           "detail":"d","urgency":"LOW","seasonalVariation":null}],"beginnerWarnings":[]}
          """;
      String annotationJson =
          "{\"regions\":[{\"label\":\"Monstera\",\"type\":\"PLANT\",\"confidence\":\"HIGH\"}]}";

      Identification completedEntity =
          Identification.builder()
              .id(2L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .carePlan(carePlanJson)
              .annotationRegions(annotationJson)
              .build();
      Identification pendingEntity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.PENDING)
              .build();

      Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
      Page<Identification> entityPage =
          new PageImpl<>(List.of(completedEntity, pendingEntity), pageable, 2);
      when(identificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
          .thenReturn(entityPage);

      IdentificationResponse mappedCompleted = IdentificationResponse.builder().id(2L).build();
      IdentificationResponse mappedPending = IdentificationResponse.builder().id(1L).build();
      when(identificationMapper.toResponse(completedEntity)).thenReturn(mappedCompleted);
      when(identificationMapper.toResponse(pendingEntity)).thenReturn(mappedPending);

      Page<IdentificationResponse> result =
          identificationService.getUserIdentifications(USER_ID, pageable);

      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getContent().get(0).getId()).isEqualTo(2L);
      assertThat(result.getContent().get(0).getCarePlan()).isNotNull();
      assertThat(result.getContent().get(0).getCarePlan().getWateringFrequencyDays()).isEqualTo(7);
      assertThat(result.getContent().get(0).getAnnotationRegions()).hasSize(1);
      assertThat(result.getContent().get(0).getAnnotationRegions().get(0).getType())
          .isEqualTo("PLANT");

      assertThat(result.getContent().get(1).getId()).isEqualTo(1L);
      assertThat(result.getContent().get(1).getCarePlan()).isNotNull();
      assertThat(result.getContent().get(1).getCarePlan().getCareCards()).isNotEmpty();
      assertThat(result.getContent().get(1).getAnnotationRegions()).isEmpty();
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

  @Nested
  @DisplayName("addCareCard()")
  class AddCareCard {

    private Identification ownedIdentificationWithPlan() {
      return Identification.builder()
          .id(1L)
          .userId(USER_ID)
          .scientificName("Monstera deliciosa")
          .carePlan(
              """
              {
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
              """)
          .build();
    }

    private AddCareCardRequest req() {
      return AddCareCardRequest.builder()
          .regionLabel("Yellowing leaf — possible overwatering")
          .adviceText("1. Remove affected leaves. 2. Reduce watering.")
          .build();
    }

    @Test
    @DisplayName("should append a new card to the existing care plan and persist it")
    void shouldAppendNewCard() {
      when(identificationRepository.findById(1L))
          .thenReturn(java.util.Optional.of(ownedIdentificationWithPlan()));

      CarePlanDto result = identificationService.addCareCard(1L, req(), USER_ID);

      assertThat(result.getCareCards()).hasSize(2);
      assertThat(result.getCareCards().get(0).getTitle()).isEqualTo("Watering");
      assertThat(result.getCareCards().get(1).getTitle())
          .isEqualTo("Yellowing leaf — possible overwatering");
      assertThat(result.getCareCards().get(1).getType()).isEqualTo("PEST");
      assertThat(result.getCareCards().get(1).getDetail())
          .isEqualTo("1. Remove affected leaves. 2. Reduce watering.");
      verify(identificationRepository).save(any(Identification.class));
    }

    @Test
    @DisplayName("should not duplicate the card or re-save when the same region is added twice")
    void shouldNotDuplicateOnRepeatedCalls() {
      Identification entity = ownedIdentificationWithPlan();
      when(identificationRepository.findById(1L)).thenReturn(java.util.Optional.of(entity));

      identificationService.addCareCard(1L, req(), USER_ID);
      // Simulate the persisted JSON reflecting the first call before the second call reads it
      entity.setCarePlan(serializedPlanWithExtraCard());

      CarePlanDto result = identificationService.addCareCard(1L, req(), USER_ID);

      assertThat(result.getCareCards()).hasSize(2);
      verify(identificationRepository, times(1)).save(any(Identification.class));
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when identification is not owned by user")
    void shouldThrowWhenNotOwned() {
      Identification foreignIdentification = Identification.builder().id(1L).userId(99L).build();
      when(identificationRepository.findById(1L))
          .thenReturn(java.util.Optional.of(foreignIdentification));

      assertThatThrownBy(() -> identificationService.addCareCard(1L, req(), USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);
      verify(identificationRepository, never()).save(any());
    }

    private String serializedPlanWithExtraCard() {
      return """
          {
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
              },
              {
                "type": "PEST",
                "title": "Yellowing leaf — possible overwatering",
                "icon": "healing",
                "summary": "Follow the steps below to treat this issue",
                "detail": "1. Remove affected leaves. 2. Reduce watering.",
                "urgency": "HIGH",
                "seasonalVariation": null
              }
            ],
            "beginnerWarnings": ["Avoid overwatering"]
          }
          """;
    }
  }

  @Nested
  @DisplayName("Species/plant resolution (T6.9)")
  class SpeciesPlantResolution {

    private static final Long IDENTIFICATION_ID = 50L;
    private static final Long SPECIES_ID = 7L;

    private Identification identificationWithSpecies(String scientificName, Long speciesId) {
      return Identification.builder()
          .id(IDENTIFICATION_ID)
          .userId(USER_ID)
          .scientificName(scientificName)
          .commonName("Swiss cheese plant")
          .speciesId(speciesId)
          .status(IdentificationStatus.COMPLETED)
          .build();
    }

    @Test
    @DisplayName("species-match: existing scientificName returns matched=true with speciesId")
    void speciesMatchReturnsMatchedWhenSpeciesExists() {
      Identification identification = identificationWithSpecies("Monstera deliciosa", null);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      com.plantpal.species.entity.Species species =
          com.plantpal.species.entity.Species.builder()
              .id(SPECIES_ID)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .build();
      when(speciesRepository.findByScientificName("Monstera deliciosa"))
          .thenReturn(Optional.of(species));

      SpeciesMatchDto result = identificationService.getSpeciesMatch(IDENTIFICATION_ID, USER_ID);

      assertThat(result.isMatched()).isTrue();
      assertThat(result.getSpeciesId()).isEqualTo(SPECIES_ID);
      verify(identificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("species-match: new scientificName returns matched=false, creates no Species row")
    void speciesMatchReturnsUnmatchedWhenSpeciesIsNew() {
      Identification identification = identificationWithSpecies("Ficus lyrata", null);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      when(speciesRepository.findByScientificName("Ficus lyrata")).thenReturn(Optional.empty());

      SpeciesMatchDto result = identificationService.getSpeciesMatch(IDENTIFICATION_ID, USER_ID);

      assertThat(result.isMatched()).isFalse();
      assertThat(result.getSpeciesId()).isNull();
      verify(speciesService, never()).findOrCreate(any(), any(), any());
    }

    @Test
    @DisplayName("resolve-species: confirmed=true delegates to findOrCreate and attaches speciesId")
    void resolveSpeciesConfirmedAttachesExistingSpecies() {
      Identification identification = identificationWithSpecies("Monstera deliciosa", null);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      com.plantpal.species.entity.Species species =
          com.plantpal.species.entity.Species.builder()
              .id(SPECIES_ID)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .build();
      when(speciesService.findOrCreate(
              eq("Monstera deliciosa"),
              any(),
              any(AiModelPreference.class),
              isNull(),
              isNull(),
              isNull()))
          .thenReturn(species);

      SpeciesMatchDto result =
          identificationService.resolveSpecies(
              IDENTIFICATION_ID, new ResolveSpeciesRequest(true, null), USER_ID);

      assertThat(result.getSpeciesId()).isEqualTo(SPECIES_ID);
      assertThat(identification.getSpeciesId()).isEqualTo(SPECIES_ID);
      verify(identificationRepository).save(identification);
      verify(speciesService)
          .findOrCreate(
              eq("Monstera deliciosa"),
              any(),
              any(AiModelPreference.class),
              isNull(),
              isNull(),
              isNull());
    }

    @Test
    @DisplayName("resolve-species: confirmed=true on new species calls findOrCreate(6-arg)")
    void resolveSpeciesConfirmedCreatesNewSpecies() {
      Identification identification = identificationWithSpecies("Ficus lyrata", null);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      com.plantpal.species.entity.Species newSpecies =
          com.plantpal.species.entity.Species.builder()
              .id(99L)
              .scientificName("Ficus lyrata")
              .commonName("Fiddle-leaf fig")
              .build();
      when(speciesService.findOrCreate(
              eq("Ficus lyrata"),
              any(),
              any(AiModelPreference.class),
              isNull(),
              isNull(),
              isNull()))
          .thenReturn(newSpecies);

      SpeciesMatchDto result =
          identificationService.resolveSpecies(
              IDENTIFICATION_ID, new ResolveSpeciesRequest(true, null), USER_ID);

      assertThat(result.getSpeciesId()).isEqualTo(99L);
      assertThat(identification.getSpeciesId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("resolve-species: confirmed=false leaves speciesId null")
    void resolveSpeciesRejectedLeavesSpeciesIdNull() {
      Identification identification = identificationWithSpecies("Ficus lyrata", null);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));

      SpeciesMatchDto result =
          identificationService.resolveSpecies(
              IDENTIFICATION_ID, new ResolveSpeciesRequest(false, null), USER_ID);

      assertThat(result.isMatched()).isFalse();
      assertThat(identification.getSpeciesId()).isNull();
      verify(identificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("plant-match: returns only ACTIVE plants of the matched species, ownership-scoped")
    void plantMatchReturnsOnlyActivePlantsOfSpecies() {
      Identification identification = identificationWithSpecies("Monstera deliciosa", SPECIES_ID);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      Plant plant =
          Plant.builder().id(100L).userId(USER_ID).nickname("Monty").speciesId(SPECIES_ID).build();
      when(plantRepository.findAllByUserIdAndSpeciesIdAndStatus(
              eq(USER_ID), eq(SPECIES_ID), eq(PlantStatus.ACTIVE), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(plant)));

      PlantMatchDto result = identificationService.getPlantMatch(IDENTIFICATION_ID, USER_ID);

      assertThat(result.getCandidatePlants()).hasSize(1);
      assertThat(result.getCandidatePlants().get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("plant-match: throws when species not yet resolved")
    void plantMatchThrowsWhenSpeciesNotResolved() {
      Identification identification = identificationWithSpecies("Monstera deliciosa", null);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));

      assertThatThrownBy(() -> identificationService.getPlantMatch(IDENTIFICATION_ID, USER_ID))
          .isInstanceOf(com.plantpal.shared.exception.ValidationException.class);
    }

    @Test
    @DisplayName("resolve-plant: existing plantId attaches and updates lastScanId")
    void resolvePlantWithExistingPlantIdAttaches() {
      Identification identification = identificationWithSpecies("Monstera deliciosa", SPECIES_ID);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      Plant plant = Plant.builder().id(100L).userId(USER_ID).nickname("Monty").build();
      when(plantRepository.findByIdAndUserId(100L, USER_ID)).thenReturn(Optional.of(plant));
      when(identificationMapper.toResponse(identification))
          .thenReturn(IdentificationResponse.builder().id(IDENTIFICATION_ID).build());

      identificationService.resolvePlant(IDENTIFICATION_ID, new ResolvePlantRequest(100L), USER_ID);

      assertThat(identification.getPlantId()).isEqualTo(100L);
      assertThat(plant.getLastScanId()).isEqualTo(IDENTIFICATION_ID);
      verify(plantRepository).save(plant);
      verify(plantService, never()).saveFromIdentification(any(), any());
    }

    @Test
    @DisplayName("resolve-plant: null plantId creates a new plant via saveFromIdentification")
    void resolvePlantWithNullPlantIdCreatesNewPlant() {
      Identification identification = identificationWithSpecies("Monstera deliciosa", SPECIES_ID);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      when(identificationMapper.toResponse(identification))
          .thenReturn(IdentificationResponse.builder().id(IDENTIFICATION_ID).build());

      identificationService.resolvePlant(IDENTIFICATION_ID, new ResolvePlantRequest(null), USER_ID);

      verify(plantService)
          .saveFromIdentification(
              argThat(req -> req.getIdentificationId().equals(IDENTIFICATION_ID)), eq(USER_ID));
      verify(plantRepository, never()).findByIdAndUserId(any(), any());
    }
  }
}
