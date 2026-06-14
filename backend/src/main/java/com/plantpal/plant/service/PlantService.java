package com.plantpal.plant.service;

import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.SaveIdentificationAsPlantRequest;
import com.plantpal.plant.dto.UpdatePlantRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlantService {

  PlantResponse createPlant(CreatePlantRequest request, Long userId);

  PlantResponse updatePlant(Long id, UpdatePlantRequest request, Long userId);

  void archivePlant(Long id, Long userId);

  Page<PlantResponse> getUserPlants(Long userId, Pageable pageable);

  PlantResponse getPlant(Long id, Long userId);

  PlantResponse saveFromIdentification(SaveIdentificationAsPlantRequest request, Long userId);
}
