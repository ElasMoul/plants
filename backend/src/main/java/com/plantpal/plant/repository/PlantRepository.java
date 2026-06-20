package com.plantpal.plant.repository;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantRepository extends JpaRepository<Plant, Long> {

  Optional<Plant> findByIdAndUserId(Long id, Long userId);

  Page<Plant> findAllByUserIdAndStatus(Long userId, PlantStatus status, Pageable pageable);

  Optional<Plant> findByIdAndUserIdAndStatus(Long id, Long userId, PlantStatus status);

  boolean existsByIdAndUserId(Long id, Long userId);

  @Query(
      "SELECT DISTINCT p.speciesId FROM Plant p "
          + "WHERE p.userId = :userId AND p.status = :status AND p.speciesId IS NOT NULL")
  Page<Long> findDistinctSpeciesIdsByUserIdAndStatus(
      @Param("userId") Long userId, @Param("status") PlantStatus status, Pageable pageable);

  List<Plant> findAllByUserIdAndStatusAndSpeciesIdIn(
      Long userId, PlantStatus status, List<Long> speciesIds);
}
