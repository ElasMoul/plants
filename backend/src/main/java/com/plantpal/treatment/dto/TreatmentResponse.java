package com.plantpal.treatment.dto;

import com.plantpal.treatment.entity.TreatmentStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentResponse {

  private Long id;
  private Long plantId;
  private Long identificationId;
  private String diseaseName;
  private String diseaseDescription;
  private String diseaseDescriptionModel;
  private String treatmentPlanModel;
  private TreatmentStatus status;
  private Long treatmentPlanId;
  private Instant startedAt;
  private Instant completedAt;
  private Boolean plantnetAgreement;
  private String eppoCode;
  private boolean needsReview;
}
