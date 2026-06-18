package com.plantpal.reminder.repository;

import com.plantpal.reminder.entity.Reminder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

  @Query(
      "SELECT r FROM Reminder r WHERE r.plantId IN :plantIds AND r.careType = 'WATERING' "
          + "AND r.enabled = true AND r.nextDueAt = (SELECT MIN(r2.nextDueAt) FROM Reminder r2 "
          + "WHERE r2.plantId = r.plantId AND r2.careType = 'WATERING' AND r2.enabled = true)")
  List<Reminder> findNearestWateringPerPlant(@Param("plantIds") List<Long> plantIds);

  List<Reminder> findByUserIdAndEnabledTrue(Long userId);

  Page<Reminder> findByUserIdAndEnabledTrue(Long userId, Pageable pageable);

  Optional<Reminder> findByIdAndUserId(Long id, Long userId);

  @Query("SELECT r FROM Reminder r WHERE r.enabled = true AND r.nextDueAt <= :now")
  List<Reminder> findAllDue(@Param("now") Instant now);

  List<Reminder> findByTreatmentPlanIdAndEnabledTrue(Long treatmentPlanId);

  List<Reminder> findByTreatmentPlanIdOrderByStepOrder(Long treatmentPlanId);
}
