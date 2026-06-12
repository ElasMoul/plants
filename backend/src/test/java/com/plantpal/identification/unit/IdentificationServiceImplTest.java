package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.PlantNetClient;
import org.mockito.Spy;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdentificationServiceImpl — Unit Tests")
class IdentificationServiceImplTest {

  @Mock private PlantNetClient plantNetClient;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private IdentificationMapper identificationMapper;
  @Mock private PlantRepository plantRepository;
  @Mock private FileStorageService fileStorageService;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private IdentificationServiceImpl identificationService;

  private static final Long USER_ID = 1L;
  private static final Long PLANT_ID = 10L;

  private MockMultipartFile validImage() {
    return new MockMultipartFile(
        "images", "plant.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
  }

  private PlantNetResponse plantNetResponse() {
    PlantNetTaxon genus = new PlantNetTaxon("Monstera");
    PlantNetTaxon family = new PlantNetTaxon("Araceae");
    PlantNetSpecies species =
        new PlantNetSpecies("Monstera deliciosa", List.of("Swiss cheese plant"), genus, family);
    PlantNetResult result = new PlantNetResult(0.92, species);
    return new PlantNetResponse(List.of(result), 499);
  }

  @Nested
  @DisplayName("identify()")
  class Identify {

    @Test
    @DisplayName("should save photo, call PlantNet, persist entity, and update plant species")
    void shouldCompleteHappyPath() throws Exception {
      List<MultipartFile> images = List.of(validImage());
      PlantNetResponse plantNetResp = plantNetResponse();

      when(fileStorageService.savePhoto(any())).thenReturn("/photos/uuid.jpg");

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
          .thenReturn(Optional.of(Plant.builder().id(PLANT_ID).userId(USER_ID).nickname("My plant").build()));

      CompletableFuture<IdentificationResponse> future =
          identificationService.identify(images, List.of("leaf"), PLANT_ID, USER_ID);
      IdentificationResponse response = future.get();

      assertThat(response).isNotNull();
      assertThat(response.getScientificName()).isEqualTo("Monstera deliciosa");
      assertThat(response.getCommonName()).isEqualTo("Swiss cheese plant");
      assertThat(response.getConfidence()).isEqualTo(0.92);
      assertThat(response.getStatus()).isEqualTo(IdentificationStatus.COMPLETED);
      assertThat(response.getTopResults()).hasSize(1);

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

      assertThatThrownBy(
              () -> identificationService.identify(images, null, null, USER_ID).get())
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
    }
  }
}
