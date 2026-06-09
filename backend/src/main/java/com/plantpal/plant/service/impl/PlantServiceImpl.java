package com.plantpal.plant.service.impl;

import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapper;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plant.service.PlantService;
import com.plantpal.shared.dto.RestPage;
import com.plantpal.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlantServiceImpl implements PlantService {

  private static final Logger log = LoggerFactory.getLogger(PlantServiceImpl.class);

  private static final String PLANTS_CACHE = "plants";
  private static final String NOT_FOUND_MSG = "Plant not found or not owned by user";

  private final PlantRepository plantRepository;
  private final PlantMapper plantMapper;

  public PlantServiceImpl(PlantRepository plantRepository, PlantMapper plantMapper) {
    this.plantRepository = plantRepository;
    this.plantMapper = plantMapper;
  }

  @Override
  @Transactional
  @CacheEvict(value = PLANTS_CACHE, allEntries = true)
  public PlantResponse createPlant(CreatePlantRequest request, Long userId) {
    Plant plant = plantMapper.toEntity(request);
    plant.setUserId(userId);
    plant.setStatus(PlantStatus.ACTIVE);

    plant = plantRepository.save(plant);
    log.info("Plant created: id={}, userId={}", plant.getId(), userId);

    return plantMapper.toResponse(plant);
  }

  @Override
  @Transactional
  @CacheEvict(value = PLANTS_CACHE, allEntries = true)
  public PlantResponse updatePlant(Long id, UpdatePlantRequest request, Long userId) {
    Plant plant = findOwnedPlant(id, userId);

    applyUpdates(plant, request);
    plant = plantRepository.save(plant);
    log.info("Plant updated: id={}, userId={}", id, userId);

    return plantMapper.toResponse(plant);
  }

  @Override
  @Transactional
  @CacheEvict(value = PLANTS_CACHE, allEntries = true)
  public void archivePlant(Long id, Long userId) {
    Plant plant = findOwnedPlant(id, userId);
    plant.setStatus(PlantStatus.ARCHIVED);
    plantRepository.save(plant);
    log.info("Plant archived: id={}, userId={}", id, userId);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(
      value = PLANTS_CACHE,
      key = "'u:' + #userId + ':p:' + #pageable.pageNumber + ':s:' + #pageable.pageSize")
  public Page<PlantResponse> getUserPlants(Long userId, Pageable pageable) {
    log.info("Fetching plants for userId={}, page={}", userId, pageable.getPageNumber());
    return new RestPage<>(plantRepository
        .findAllByUserIdAndStatus(userId, PlantStatus.ACTIVE, pageable)
        .map(plantMapper::toResponse));
  }

  @Override
  @Transactional(readOnly = true)
  public PlantResponse getPlant(Long id, Long userId) {
    Plant plant = findOwnedPlant(id, userId);
    log.info("Fetched plant: id={}, userId={}", id, userId);
    return plantMapper.toResponse(plant);
  }

  private Plant findOwnedPlant(Long id, Long userId) {
    return plantRepository
        .findByIdAndUserIdAndStatus(id, userId, PlantStatus.ACTIVE)
        .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MSG));
  }

  private void applyUpdates(Plant plant, UpdatePlantRequest request) {
    if (request.getNickname() != null) { plant.setNickname(request.getNickname()); }
    if (request.getSpecies() != null) { plant.setSpecies(request.getSpecies()); }
    if (request.getCommonName() != null) { plant.setCommonName(request.getCommonName()); }
    if (request.getPhotoUrl() != null) { plant.setPhotoUrl(request.getPhotoUrl()); }
    if (request.getLocation() != null) { plant.setLocation(request.getLocation()); }
    if (request.getNotes() != null) { plant.setNotes(request.getNotes()); }
    if (request.getAcquiredAt() != null) { plant.setAcquiredAt(request.getAcquiredAt()); }
  }
}
