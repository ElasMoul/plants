package com.plantpal.journal.repository;

import com.plantpal.journal.entity.PlantJournalEvent;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantJournalEventRepository extends JpaRepository<PlantJournalEvent, Long> {

  Page<PlantJournalEvent> findAllByPlantIdAndUserId(Long plantId, Long userId, Pageable pageable);

  Optional<PlantJournalEvent> findByIdAndPlantIdAndUserId(Long id, Long plantId, Long userId);
}
