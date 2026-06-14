package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.identification.dto.plantnet.PlantNetSpecies;
import com.plantpal.identification.dto.plantnet.PlantNetTaxon;
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
import com.plantpal.shared.storage.FileStorageService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

  @Mock private PlantNetClient plantNetClient;
  @Mock private DeepSeekClient deepSeekClient;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private IdentificationMapper identificationMapper;
  @Mock private PlantRepository plantRepository;
  @Mock private ReminderRepository reminderRepository;
  @Mock private FileStorageService fileStorageService;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  private IdentificationServiceImpl identificationService;

  private static final Long USER_ID = 1L;
  private static final Long PLANT_ID = 10L;

  @BeforeEach
  void setUp() {
    identificationService =
        new IdentificationServiceImpl(
            plantNetClient,
            deepSeekClient,
            identificationRepository,
            identificationMapper,
            plantRepository,
            reminderRepository,
            fileStorageService,
            objectMapper);
  }

  private MockMultipartFile validImage() {
    return new MockMultipartFile(
        "images", "plant.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
  }

  private PlantNetResponse plantNetResponse() {
    PlantNetTaxon genus = new PlantNetTaxon("Monstera");
    PlantNetTaxon family = new PlantNetTaxon("Araceae");
    PlantNetSpecies species =
        new PlantNetSpecies("Monstera deliciosa", List.of("Swiss cheese plant"), genus, family);
    PlantNetResult result = new PlantNetResult(0.92, species);
    return new PlantNetResponse(List.of(result), 499);
  }

  private String validCarePlanJson() {
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
            }
          ],
          "beginnerWarnings": ["Avoid overwatering"]
        }
        """;
  }

  @Nested
  @DisplayName("identify()")
  class Identify {

    @Test
    @DisplayName("should complete happy path: PlantNet + DeepSeek + persist + update plant species")
    void shouldCompleteHappyPath() throws Exception {
      List<MultipartFile> images = List.of(validImage());
      PlantNetResponse plantNetResp = plantNetResponse();

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(deepSeekClient.generateCarePlan(any(), any(), any())).thenReturn(validCarePlanJson());

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
              .confidence(0.92)
              .status(IdentificationStatus.COMPLETED)
              .build();

      when(identificationRepository.save(any()))
          .thenReturn(pendingEntity)
          .thenReturn(completedEntity);
      when(plantNetClient.identify(any(), any())).thenReturn(plantNetResp);
      when(plantRepository.existsByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(true);
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID))
          .thenReturn(
              Optional.of(
                  Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("My plant").build()));

      CompletableFuture<IdentificationResponse> future =
          identificationService.identify(images, List.of("leaf"), PLANT_ID, USER_ID);
      IdentificationResponse response = future.get();

      assertThat(response).isNotNull();
      assertThat(response.getScientificName()).isEqualTo("Monstera deliciosa");
      assertThat(response.getCommonName()).isEqualTo("Swiss cheese plant");
      assertThat(response.getConfidence()).isEqualTo(0.92);
      assertThat(response.getStatus()).isEqualTo(IdentificationStatus.COMPLETED);
      assertThat(response.getTopResults()).hasSize(1);
      assertThat(response.getCarePlan()).isNotNull();
      assertThat(response.getCarePlan().getWateringFrequencyDays()).isEqualTo(7);

      verify(fileStorageService).savePhoto(any());
      verify(plantNetClient).identify(eq(images), eq(List.of("leaf")));
      verify(identificationRepository, times(2)).save(any(Identification.class));
      verify(plantRepository).save(any(Plant.class));
    }

    @Test
    @DisplayName("should mark entity FAILED when PlantNet returns 404")
    void shouldMarkEntityFailedOnPlantNet404() {
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
      when(plantNetClient.identify(any(), any()))
          .thenThrow(new PlantPalException("No species match found", 404));

      assertThatThrownBy(() -> identificationService.identify(images, null, null, USER_ID).get())
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("No species match found");

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository, times(2)).save(captor.capture());
      Identification lastSave = captor.getAllValues().get(1);
      assertThat(lastSave.getStatus()).isEqualTo(IdentificationStatus.FAILED);
    }

    @Test
    @DisplayName("should NOT update plant species when plantId is not owned by user")
    void shouldNotUpdatePlantWhenNotOwned() throws Exception {
      List<MultipartFile> images = List.of(validImage());
      PlantNetResponse plantNetResp = plantNetResponse();
      Long foreignPlantId = 99L;

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(deepSeekClient.generateCarePlan(any(), any(), any())).thenReturn(validCarePlanJson());
      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(foreignPlantId)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .confidence(0.92)
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantNetClient.identify(any(), any())).thenReturn(plantNetResp);
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
    @DisplayName("should parse valid DeepSeek JSON into CarePlanDto with all fields mapped")
    void shouldParseValidCarePlan() throws Exception {
      List<MultipartFile> images = List.of(validImage());

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(deepSeekClient.generateCarePlan(any(), any(), any())).thenReturn(validCarePlanJson());

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .confidence(0.92)
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantNetClient.identify(any(), any())).thenReturn(plantNetResponse());

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
      when(deepSeekClient.generateCarePlan(any(), any(), any())).thenReturn("not valid json {{{");

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantNetClient.identify(any(), any())).thenReturn(plantNetResponse());

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getCarePlan()).isNotNull();
      assertThat(response.getCarePlan().getCareCards()).isNotEmpty();
      assertThat(response.getCarePlan().getCareCards().get(0).getType()).isEqualTo("WATERING");
    }

    @Test
    @DisplayName("should return fallback care plan when DeepSeek service throws")
    void shouldReturnFallbackWhenDeepSeekThrows() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(deepSeekClient.generateCarePlan(any(), any(), any()))
          .thenThrow(new PlantPalException("Care plan service unavailable", 503));

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantNetClient.identify(any(), any())).thenReturn(plantNetResponse());

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getCarePlan()).isNotNull();
      assertThat(response.getCarePlan().getCareCards()).isNotEmpty();
    }

    @Test
    @DisplayName("fallback care plan always has at least one care card")
    void fallbackCareCardNeverNull() throws Exception {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(deepSeekClient.generateCarePlan(any(), any(), any())).thenReturn(null);

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantNetClient.identify(any(), any())).thenReturn(plantNetResponse());

      IdentificationResponse response =
          identificationService.identify(List.of(validImage()), null, null, USER_ID).get();

      assertThat(response.getCarePlan().getCareCards()).isNotNull().isNotEmpty();
    }
  }

  @Nested
  @DisplayName("reminder auto-creation")
  class ReminderCreation {

    private String carePlanWithFertilizing() {
      return """
          {
            "wateringFrequencyDays": 7,
            "fertilizingFrequencyDays": 30,
            "repottingFrequencyMonths": 6,
            "careCards": [{"type":"WATERING","title":"W","icon":"water_drop","summary":"s","detail":"d","urgency":"LOW","seasonalVariation":null}],
            "beginnerWarnings": []
          }
          """;
    }

    private String carePlanNoFertilizing() {
      return """
          {
            "wateringFrequencyDays": 14,
            "fertilizingFrequencyDays": 0,
            "repottingFrequencyMonths": 24,
            "careCards": [{"type":"WATERING","title":"W","icon":"water_drop","summary":"s","detail":"d","urgency":"LOW","seasonalVariation":null}],
            "beginnerWarnings": []
          }
          """;
    }

    private void stubHappyPath(String carePlanJson) {
      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");
      when(deepSeekClient.generateCarePlan(any(), any(), any())).thenReturn(carePlanJson);

      Identification entity =
          Identification.builder()
              .id(1L)
              .userId(USER_ID)
              .plantId(PLANT_ID)
              .status(IdentificationStatus.COMPLETED)
              .scientificName("Monstera deliciosa")
              .commonName("Swiss cheese plant")
              .confidence(0.92)
              .build();
      when(identificationRepository.save(any())).thenReturn(entity);
      when(plantNetClient.identify(any(), any())).thenReturn(plantNetResponse());
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
      stubHappyPath(carePlanWithFertilizing());

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
      stubHappyPath(carePlanNoFertilizing());

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
      stubHappyPath(carePlanWithFertilizing());

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
}
