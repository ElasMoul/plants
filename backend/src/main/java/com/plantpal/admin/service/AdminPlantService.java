package com.plantpal.admin.service;

import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.PlantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminPlantService {
  Page<PlantResponse> list(Long userId, PlantStatus status, Pageable page);

  PlantResponse update(Long userId, Long plantId, UpdatePlantRequest request, Long actorId);

  void archive(Long userId, Long plantId, Long actorId);

  PlantResponse restore(Long userId, Long plantId, Long actorId);
}
