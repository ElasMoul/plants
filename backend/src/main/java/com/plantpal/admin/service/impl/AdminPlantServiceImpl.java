package com.plantpal.admin.service.impl;

import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.admin.service.AdminPlantService;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapper;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.PlantService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminPlantServiceImpl implements AdminPlantService {
  private final PlantRepository plants;
  private final PlantMapper mapper;
  private final PlantService service;
  private final AdminRepository audit;
  private final com.plantpal.admin.repository.AdminPlantHistoryRepository history;

  public AdminPlantServiceImpl(
      PlantRepository plants,
      PlantMapper mapper,
      PlantService service,
      AdminRepository audit,
      com.plantpal.admin.repository.AdminPlantHistoryRepository history) {
    this.plants = plants;
    this.mapper = mapper;
    this.service = service;
    this.audit = audit;
    this.history = history;
  }

  @Override
  @Transactional(readOnly = true)
  public com.plantpal.admin.dto.AdminDtos.PlantDetail view(
      Long userId, Long plantId, Pageable page) {
    var plant =
        plants
            .findByIdAndUserId(plantId, userId)
            .orElseThrow(
                () ->
                    new com.plantpal.shared.exception.ResourceNotFoundException("Plant not found"));
    return new com.plantpal.admin.dto.AdminDtos.PlantDetail(
        mapper.toResponse(plant), history.history(userId, plantId, page));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PlantResponse> list(Long userId, PlantStatus status, Pageable page) {
    return plants.findAllByUserIdAndStatus(userId, status, page).map(mapper::toResponse);
  }

  @Override
  public PlantResponse update(Long userId, Long plantId, UpdatePlantRequest request, Long actorId) {
    PlantResponse result = service.updatePlant(plantId, request, userId);
    audit.record(actorId, "Plant updated for user #" + userId, "plant:" + plantId);
    return result;
  }

  @Override
  public void archive(Long userId, Long plantId, Long actorId) {
    service.archivePlant(plantId, userId);
    audit.record(actorId, "Plant archived for user #" + userId, "plant:" + plantId);
  }

  @Override
  public PlantResponse restore(Long userId, Long plantId, Long actorId) {
    PlantResponse result = service.restorePlant(plantId, userId);
    audit.record(actorId, "Plant restored for user #" + userId, "plant:" + plantId);
    return result;
  }
}
