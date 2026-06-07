package com.plantpal.plant.repository;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantRepository extends JpaRepository<Plant, Long> {

  Optional<Plant> findByIdAndUserId(Long id, Long userId);

  Page<Plant> findAllByUserIdAndStatus(Long userId, PlantStatus status, Pageable pageable);

  Optional<Plant> findByIdAndUserIdAndStatus(Long id, Long userId, PlantStatus status);

  boolean existsByIdAndUserId(Long id, Long userId);
}
