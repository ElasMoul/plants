package com.plantpal.plant.unit;

import static com.plantpal.testdata.PlantTestDataBuilder.aCreatePlantRequest;
import static com.plantpal.testdata.PlantTestDataBuilder.aPlant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapper;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.impl.PlantServiceImpl;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
  }
}
