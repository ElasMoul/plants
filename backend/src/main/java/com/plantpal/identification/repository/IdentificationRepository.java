package com.plantpal.identification.repository;

import com.plantpal.identification.entity.Identification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentificationRepository extends JpaRepository<Identification, Long> {

  Page<Identification> findByPlantIdOrderByCreatedAtDesc(Long plantId, Pageable pageable);

  Page<Identification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
