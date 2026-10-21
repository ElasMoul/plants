package com.plantpal.plant.unit;

import static com.plantpal.testdata.PlantTestDataBuilder.aPlant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.LightExposure;
import com.plantpal.plant.entity.Placement;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapperImpl;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.impl.PlantServiceImpl;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.user.service.UsageService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("Plant placement and light exposure (PLA-107)")
class PlantPlacementLightTest {

  @Mock private UsageService usage;
  @Mock private PlantRepository plantRepository;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private ReminderRepository reminderRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();
  @Spy private PlantMapperImpl plantMapper = new PlantMapperImpl();

  @InjectMocks private PlantServiceImpl plantService;

  @Test
  @DisplayName("create persists placement and light from the request")
  void createStoresValues() {
    var request = new CreatePlantRequest();
    request.setNickname("Monty");
    request.setPlacement(Placement.GREENHOUSE);
    request.setLightExposure(LightExposure.BRIGHT);
    when(plantRepository.save(org.mockito.ArgumentMatchers.any(Plant.class)))
        .thenAnswer(i -> i.getArgument(0));

    var result = plantService.createPlant(request, 1L);

    assertThat(result.getPlacement()).isEqualTo(Placement.GREENHOUSE);
    assertThat(result.getLightExposure()).isEqualTo(LightExposure.BRIGHT);
  }

  @Test
  @DisplayName("update changes values, and omitting them leaves them unchanged")
  void updateChangesThenKeeps() {
    var plant = aPlant().withId(1L).withUserId(1L).withStatus(PlantStatus.ACTIVE).build();
    plant.setPlacement(Placement.INDOOR);
    plant.setLightExposure(LightExposure.LOW);
    when(plantRepository.findByIdAndUserIdAndStatus(1L, 1L, PlantStatus.ACTIVE))
        .thenReturn(Optional.of(plant));
    when(plantRepository.save(plant)).thenReturn(plant);

    var change = new UpdatePlantRequest();
    change.setPlacement(Placement.OUTDOOR);
    change.setLightExposure(LightExposure.DIRECT_SUN);
    var changed = plantService.updatePlant(1L, change, 1L);
    assertThat(changed.getPlacement()).isEqualTo(Placement.OUTDOOR);
    assertThat(changed.getLightExposure()).isEqualTo(LightExposure.DIRECT_SUN);

    var kept = plantService.updatePlant(1L, new UpdatePlantRequest(), 1L);
    assertThat(kept.getPlacement()).isEqualTo(Placement.OUTDOOR);
    assertThat(kept.getLightExposure()).isEqualTo(LightExposure.DIRECT_SUN);
  }

  @Test
  @DisplayName("legacy plant with no values reads back as null and survives an update")
  void legacyRowsStayNull() {
    var plant = aPlant().withId(2L).withUserId(1L).withStatus(PlantStatus.ACTIVE).build();
    when(plantRepository.findByIdAndUserIdAndStatus(2L, 1L, PlantStatus.ACTIVE))
        .thenReturn(Optional.of(plant));
    when(plantRepository.save(plant)).thenReturn(plant);
    var update = new UpdatePlantRequest();
    update.setNickname("Renamed");

    var result = plantService.updatePlant(2L, update, 1L);

    assertThat(result.getNickname()).isEqualTo("Renamed");
    assertThat(result.getPlacement()).isNull();
    assertThat(result.getLightExposure()).isNull();
  }
}
