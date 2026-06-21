package com.plantpal.treatment.repository;

import com.plantpal.treatment.entity.Treatment;
import com.plantpal.treatment.entity.TreatmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

  Optional<Treatment> findByIdAndUserId(Long id, Long userId);

  List<Treatment> findByPlantIdAndUserId(Long plantId, Long userId);

  List<Treatment> findByPlantIdAndDiseaseNameAndStatusIn(
      Long plantId, String diseaseName, List<TreatmentStatus> statuses);

  Optional<Treatment> findFirstByPlantIdAndUserIdAndStatusInOrderByCreatedAtDesc(
      Long plantId, Long userId, List<TreatmentStatus> statuses);

  List<Treatment> findByPlantIdAndUserIdAndStatusInOrderByCreatedAtDesc(
      Long plantId, Long userId, List<TreatmentStatus> statuses);

  List<Treatment> findByTreatmentPlanId(Long treatmentPlanId);
}
