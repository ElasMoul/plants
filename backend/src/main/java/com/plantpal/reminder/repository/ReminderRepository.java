package com.plantpal.reminder.repository;

import com.plantpal.reminder.entity.Reminder;
import java.util.List;
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
}
