package com.plantpal.plant.unit;

import static com.plantpal.testdata.PlantTestDataBuilder.aCreatePlantRequest;
import static com.plantpal.testdata.PlantTestDataBuilder.aPlant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.SaveIdentificationAsPlantRequest;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapper;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.impl.PlantServiceImpl;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlantService — Unit Tests")
class PlantServiceTest {

  @Mock private PlantRepository plantRepository;
  @Mock private PlantMapper plantMapper;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private ReminderRepository reminderRepository;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private PlantServiceImpl plantService;

  @Nested
  @DisplayName("createPlant()")
  class CreatePlant {

    @Test
    @DisplayName("should save plant with ACTIVE status and the caller's userId")
    void shouldCreatePlantSuccessfully() {
      // Given
      var request = aCreatePlantRequest().withNickname("My Monstera").build();
      var savedPlant = aPlant().withId(10L).withUserId(1L).build();
      var expectedResponse = PlantResponse.builder().id(10L).nickname("My Monstera").build();

      when(plantMapper.toEntity(request)).thenReturn(aPlant().build());
      when(plantRepository.save(any(Plant.class))).thenReturn(savedPlant);
      when(plantMapper.toResponse(savedPlant)).thenReturn(expectedResponse);

      // When
      PlantResponse result = plantService.createPlant(request, 1L);

      // Then
      assertThat(result.getId()).isEqualTo(10L);

      ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(PlantStatus.ACTIVE);
      assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("getPlant()")
  class GetPlant {

    @Test
    @DisplayName("should return plant when id and userId match an ACTIVE plant")
    void shouldReturnPlantWhenFound() {
      // Given
      var plant = aPlant().withId(1L).withUserId(1L).build();
      var response = PlantResponse.builder().id(1L).build();
      when(plantRepository.findByIdAndUserIdAndStatus(1L, 1L, PlantStatus.ACTIVE))
          .thenReturn(Optional.of(plant));
      when(plantMapper.toResponse(plant)).thenReturn(response);

      // When
      PlantResponse result = plantService.getPlant(1L, 1L);

      // Then
      assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when plant does not exist")
    void shouldThrowWhenPlantNotFound() {
      // Given
      when(plantRepository.findByIdAndUserIdAndStatus(99L, 1L, PlantStatus.ACTIVE))
          .thenReturn(Optional.empty());

      // When / Then
      assertThatThrownBy(() -> plantService.getPlant(99L, 1L))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when plant belongs to a different user")
    void shouldThrowWhenPlantBelongsToAnotherUser() {
      // Given — plant id=1 exists but belongs to userId=2, not userId=99
      when(plantRepository.findByIdAndUserIdAndStatus(1L, 99L, PlantStatus.ACTIVE))
          .thenReturn(Optional.empty());

      // When / Then — same exception as "not found" to avoid existence leakage
      assertThatThrownBy(() -> plantService.getPlant(1L, 99L))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("archivePlant()")
  class ArchivePlant {

    @Test
    @DisplayName("should set status to ARCHIVED and never call delete")
    void shouldArchivePlantWithoutDeleting() {
      // Given
      var plant = aPlant().withId(1L).withUserId(1L).withStatus(PlantStatus.ACTIVE).build();
      when(plantRepository.findByIdAndUserIdAndStatus(1L, 1L, PlantStatus.ACTIVE))
          .thenReturn(Optional.of(plant));
      when(plantRepository.save(any(Plant.class))).thenReturn(plant);

      // When
      plantService.archivePlant(1L, 1L);

      // Then
      ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(PlantStatus.ARCHIVED);
      verify(plantRepository, never()).delete(any());
      verify(plantRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("should disable all enabled reminders for the archived plant")
    void shouldDisableRemindersOnArchive() {
      // Given
      var plant = aPlant().withId(1L).withUserId(1L).withStatus(PlantStatus.ACTIVE).build();
      when(plantRepository.findByIdAndUserIdAndStatus(1L, 1L, PlantStatus.ACTIVE))
          .thenReturn(Optional.of(plant));
      when(plantRepository.save(any(Plant.class))).thenReturn(plant);

      Reminder watering = Reminder.builder().id(1L).plantId(1L).enabled(true).build();
      Reminder fertilizing = Reminder.builder().id(2L).plantId(1L).enabled(true).build();
      when(reminderRepository.findByPlantIdAndEnabledTrue(1L))
          .thenReturn(java.util.List.of(watering, fertilizing));

      // When
      plantService.archivePlant(1L, 1L);

      // Then
      assertThat(watering.isEnabled()).isFalse();
      assertThat(fertilizing.isEnabled()).isFalse();
      verify(reminderRepository).saveAll(java.util.List.of(watering, fertilizing));
    }
  }

  @Nested
  @DisplayName("getUserPlants()")
  class GetUserPlants {

    @Test
    @DisplayName("should return a page of ACTIVE plants for the given user")
    void shouldReturnPageOfPlants() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      var plant = aPlant().withUserId(1L).build();
      Page<Plant> plantPage = new PageImpl<>(java.util.List.of(plant));
      var response = PlantResponse.builder().id(1L).build();

      when(plantRepository.findAllByUserIdAndStatus(1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(plantPage);
      when(plantMapper.toResponse(plant)).thenReturn(response);

      // When
      Page<PlantResponse> result = plantService.getUserPlants(1L, pageable);

      // Then
      assertThat(result.getTotalElements()).isEqualTo(1);
      assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("should return an empty page when user has no active plants")
    void shouldReturnEmptyPageWhenNoPlants() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      when(plantRepository.findAllByUserIdAndStatus(1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(Page.empty());

      // When
      Page<PlantResponse> result = plantService.getUserPlants(1L, pageable);

      // Then
      assertThat(result.getTotalElements()).isZero();
      assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName(
        "should populate healthStatus from latest identification and nextWaterDays from nearest"
            + " watering reminder")
    void shouldEnrichWithHealthAndWater() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      var plant = aPlant().withId(1L).withUserId(1L).build();
      Page<Plant> plantPage = new PageImpl<>(java.util.List.of(plant));
      var response = PlantResponse.builder().id(1L).build();

      when(plantRepository.findAllByUserIdAndStatus(1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(plantPage);
      when(plantMapper.toResponse(plant)).thenReturn(response);

      Identification latest =
          Identification.builder().id(5L).plantId(1L).healthStatus("HEALTHY").build();
      when(identificationRepository.findLatestPerPlant(java.util.List.of(1L)))
          .thenReturn(java.util.List.of(latest));

      // +12h margin: guards against ChronoUnit.DAYS truncation if a few ms elapse before the
      // service re-reads Instant.now().
      java.time.Instant nextDueAt =
          java.time.Instant.now()
              .plus(3, java.time.temporal.ChronoUnit.DAYS)
              .plus(12, java.time.temporal.ChronoUnit.HOURS);
      Reminder reminder =
          Reminder.builder()
              .id(7L)
              .plantId(1L)
              .careType(com.plantpal.reminder.entity.CareType.WATERING)
              .nextDueAt(nextDueAt)
              .enabled(true)
              .build();
      when(reminderRepository.findNearestWateringPerPlant(java.util.List.of(1L)))
          .thenReturn(java.util.List.of(reminder));

      // When
      Page<PlantResponse> result = plantService.getUserPlants(1L, pageable);

      // Then
      PlantResponse enriched = result.getContent().get(0);
      assertThat(enriched.getHealthStatus()).isEqualTo("HEALTHY");
      assertThat(enriched.getNextWaterDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("should leave healthStatus and nextWaterDays null when neither exists")
    void shouldLeaveHealthAndWaterNullWhenAbsent() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      var plant = aPlant().withId(1L).withUserId(1L).build();
      Page<Plant> plantPage = new PageImpl<>(java.util.List.of(plant));
      var response = PlantResponse.builder().id(1L).build();

      when(plantRepository.findAllByUserIdAndStatus(1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(plantPage);
      when(plantMapper.toResponse(plant)).thenReturn(response);
      when(identificationRepository.findLatestPerPlant(java.util.List.of(1L)))
          .thenReturn(java.util.List.of());
      when(reminderRepository.findNearestWateringPerPlant(java.util.List.of(1L)))
          .thenReturn(java.util.List.of());

      // When
      Page<PlantResponse> result = plantService.getUserPlants(1L, pageable);

      // Then
      PlantResponse enriched = result.getContent().get(0);
      assertThat(enriched.getHealthStatus()).isNull();
      assertThat(enriched.getNextWaterDays()).isNull();
    }

    @Test
    @DisplayName("should return a negative nextWaterDays when the watering reminder is overdue")
    void shouldReturnNegativeNextWaterDaysWhenOverdue() {
      // Given
      Pageable pageable = PageRequest.of(0, 20);
      var plant = aPlant().withId(1L).withUserId(1L).build();
      Page<Plant> plantPage = new PageImpl<>(java.util.List.of(plant));
      var response = PlantResponse.builder().id(1L).build();

      when(plantRepository.findAllByUserIdAndStatus(1L, PlantStatus.ACTIVE, pageable))
          .thenReturn(plantPage);
      when(plantMapper.toResponse(plant)).thenReturn(response);
      when(identificationRepository.findLatestPerPlant(java.util.List.of(1L)))
          .thenReturn(java.util.List.of());

      // -12h margin: keeps the value solidly within the -2 day bucket despite truncation.
      java.time.Instant overdueAt =
          java.time.Instant.now()
              .minus(2, java.time.temporal.ChronoUnit.DAYS)
              .minus(12, java.time.temporal.ChronoUnit.HOURS);
      Reminder reminder =
          Reminder.builder()
              .id(7L)
              .plantId(1L)
              .careType(com.plantpal.reminder.entity.CareType.WATERING)
              .nextDueAt(overdueAt)
              .enabled(true)
              .build();
      when(reminderRepository.findNearestWateringPerPlant(java.util.List.of(1L)))
          .thenReturn(java.util.List.of(reminder));

      // When
      Page<PlantResponse> result = plantService.getUserPlants(1L, pageable);

      // Then
      assertThat(result.getContent().get(0).getNextWaterDays()).isEqualTo(-2);
    }
  }

  @Nested
  @DisplayName("updatePlant()")
  class UpdatePlant {

    @Test
    @DisplayName("should apply non-null fields and return updated response")
    void shouldUpdateSuccessfully() {
      // Given
      var request = new UpdatePlantRequest();
      request.setNickname("Renamed Monstera");
      request.setLocation("Bedroom");

      var existing = aPlant().withId(1L).withUserId(1L).withStatus(PlantStatus.ACTIVE).build();
      var response = PlantResponse.builder().id(1L).nickname("Renamed Monstera").build();

      when(plantRepository.findByIdAndUserIdAndStatus(1L, 1L, PlantStatus.ACTIVE))
          .thenReturn(Optional.of(existing));
      when(plantRepository.save(existing)).thenReturn(existing);
      when(plantMapper.toResponse(existing)).thenReturn(response);

      // When
      PlantResponse result = plantService.updatePlant(1L, request, 1L);

      // Then
      assertThat(result.getNickname()).isEqualTo("Renamed Monstera");
      verify(plantRepository).save(existing);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException and never save when plant is not found")
    void shouldThrowAndNeverSaveWhenNotFound() {
      // Given
      when(plantRepository.findByIdAndUserIdAndStatus(99L, 1L, PlantStatus.ACTIVE))
          .thenReturn(Optional.empty());

      // When / Then
      assertThatThrownBy(() -> plantService.updatePlant(99L, new UpdatePlantRequest(), 1L))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(plantRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("saveFromIdentification()")
  class SaveFromIdentification {

    private static final Long USER_ID = 1L;
    private static final Long IDENTIFICATION_ID = 42L;

    private Identification identificationWith(
        String commonName, String scientificName, String carePlanJson) {
      return Identification.builder()
          .id(IDENTIFICATION_ID)
          .userId(USER_ID)
          .status(IdentificationStatus.COMPLETED)
          .commonName(commonName)
          .scientificName(scientificName)
          .photoUrl("/photos/plant.jpg")
          .carePlan(carePlanJson)
          .build();
    }

    private void stubSave(Identification identification) {
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      Plant saved = aPlant().withId(99L).withUserId(USER_ID).build();
      when(plantRepository.save(any(Plant.class))).thenReturn(saved);
      when(identificationRepository.save(any(Identification.class))).thenReturn(identification);
      when(plantMapper.toResponse(saved)).thenReturn(PlantResponse.builder().id(99L).build());
    }

    @Test
    @DisplayName("should use request nickname when provided")
    void shouldUseRequestNickname() {
      Identification identification =
          identificationWith("Swiss cheese plant", "Monstera deliciosa", null);
      stubSave(identification);
      var request = new SaveIdentificationAsPlantRequest(IDENTIFICATION_ID, "My Monstera", null);

      plantService.saveFromIdentification(request, USER_ID);

      ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(captor.capture());
      assertThat(captor.getValue().getNickname()).isEqualTo("My Monstera");
    }

    @Test
    @DisplayName("should fall back to commonName when nickname not provided")
    void shouldFallBackToCommonName() {
      Identification identification =
          identificationWith("Swiss cheese plant", "Monstera deliciosa", null);
      stubSave(identification);
      var request = new SaveIdentificationAsPlantRequest(IDENTIFICATION_ID, null, null);

      plantService.saveFromIdentification(request, USER_ID);

      ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(captor.capture());
      assertThat(captor.getValue().getNickname()).isEqualTo("Swiss cheese plant");
    }

    @Test
    @DisplayName("should fall back to scientificName when commonName is null")
    void shouldFallBackToScientificName() {
      Identification identification = identificationWith(null, "Monstera deliciosa", null);
      stubSave(identification);
      var request = new SaveIdentificationAsPlantRequest(IDENTIFICATION_ID, null, null);

      plantService.saveFromIdentification(request, USER_ID);

      ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(captor.capture());
      assertThat(captor.getValue().getNickname()).isEqualTo("Monstera deliciosa");
    }

    @Test
    @DisplayName("should fall back to 'My Plant' when both names are null")
    void shouldFallBackToMyPlant() {
      Identification identification = identificationWith(null, null, null);
      stubSave(identification);
      var request = new SaveIdentificationAsPlantRequest(IDENTIFICATION_ID, null, null);

      plantService.saveFromIdentification(request, USER_ID);

      ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
      verify(plantRepository).save(captor.capture());
      assertThat(captor.getValue().getNickname()).isEqualTo("My Plant");
    }

    @Test
    @DisplayName("should link identification.plantId to the new plant")
    void shouldUpdateIdentificationPlantId() {
      Identification identification = identificationWith("Monstera", "Monstera deliciosa", null);
      stubSave(identification);
      var request = new SaveIdentificationAsPlantRequest(IDENTIFICATION_ID, "Test", null);

      plantService.saveFromIdentification(request, USER_ID);

      ArgumentCaptor<Identification> captor = ArgumentCaptor.forClass(Identification.class);
      verify(identificationRepository).save(captor.capture());
      assertThat(captor.getValue().getPlantId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("should create reminders when care plan JSON is present")
    void shouldCreateRemindersWhenCarePlanPresent() {
      String carePlanJson =
          """
          {"wateringFrequencyDays":7,"fertilizingFrequencyDays":30,
           "repottingFrequencyMonths":12,"careCards":[],"beginnerWarnings":[]}
          """;
      Identification identification =
          identificationWith("Monstera", "Monstera deliciosa", carePlanJson);
      stubSave(identification);
      when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));
      var request = new SaveIdentificationAsPlantRequest(IDENTIFICATION_ID, "Test", null);

      plantService.saveFromIdentification(request, USER_ID);

      verify(reminderRepository, times(3)).save(any(Reminder.class));
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when identification not owned by user")
    void shouldThrowWhenIdentificationNotOwned() {
      Identification identification = identificationWith("Monstera", "Monstera deliciosa", null);
      identification.setUserId(999L);
      when(identificationRepository.findById(IDENTIFICATION_ID))
          .thenReturn(Optional.of(identification));
      var request = new SaveIdentificationAsPlantRequest(IDENTIFICATION_ID, null, null);

      assertThatThrownBy(() -> plantService.saveFromIdentification(request, USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(plantRepository, never()).save(any(Plant.class));
    }
  }
}
