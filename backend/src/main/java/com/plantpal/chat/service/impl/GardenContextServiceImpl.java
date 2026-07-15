package com.plantpal.chat.service.impl;

import com.plantpal.chat.service.GardenContextService;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GardenContextServiceImpl implements GardenContextService {

  private static final String GARDEN_CACHE = "garden";
  private static final int GARDEN_CONTEXT_PAGE_SIZE = 50;

  private final PlantRepository plantRepository;

  public GardenContextServiceImpl(PlantRepository plantRepository) {
    this.plantRepository = plantRepository;
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = GARDEN_CACHE, key = "#userId")
  public String buildGardenContext(Long userId) {
    Page<Plant> plants =
        plantRepository.findAllByUserIdAndStatus(
            userId, PlantStatus.ACTIVE, PageRequest.of(0, GARDEN_CONTEXT_PAGE_SIZE));
    if (plants.isEmpty()) {
      return "No plants in the garden yet.";
    }
    return plants.getContent().stream().map(this::formatPlant).collect(Collectors.joining("\n"));
  }

  private String formatPlant(Plant plant) {
    String label =
        plant.getCommonName() != null
            ? plant.getCommonName()
            : (plant.getSpecies() != null ? plant.getSpecies() : "unknown species");
    return "- " + plant.getNickname() + " (" + label + ")";
  }
}
