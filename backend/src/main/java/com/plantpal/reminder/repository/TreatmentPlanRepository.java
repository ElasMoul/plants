package com.plantpal.reminder.repository;

import com.plantpal.reminder.entity.TreatmentPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, Long> {

  Optional<TreatmentPlan> findByIdAndUserId(Long id, Long userId);

  List<TreatmentPlan> findByPlantIdAndUserId(Long plantId, Long userId);
}
