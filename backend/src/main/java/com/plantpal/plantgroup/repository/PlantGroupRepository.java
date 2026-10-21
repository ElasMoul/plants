package com.plantpal.plantgroup.repository;

import com.plantpal.plantgroup.entity.PlantGroup;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantGroupRepository extends JpaRepository<PlantGroup, Long> {

  Page<PlantGroup> findAllByUserId(Long userId, Pageable pageable);

  Optional<PlantGroup> findByIdAndUserId(Long id, Long userId);
}
