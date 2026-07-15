package com.plantpal.identification.dto;

import com.plantpal.identification.entity.IdentificationStageStatus;
import com.plantpal.identification.entity.IdentificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentificationResponse {

  private Long id;
  private Long plantId;

  @Schema(example = "Monstera deliciosa")
  private String scientificName;

  @Schema(example = "Swiss Cheese Plant")
  private String commonName;

  @Schema(example = "0.92")
  private Double confidence;

  @Schema(example = "ISSUES_DETECTED")
  private String healthStatus;

  private String healthNotes;

  @Schema(example = "COMPLETED")
  private IdentificationStatus status;

  private String photoUrl;
  private Instant createdAt;
  private CarePlanDto carePlan;
  private List<AnnotationRegionDto> annotationRegions;
  private Integer sourceImageWidth;
  private Integer sourceImageHeight;
  private String aiModelUsed;
  private Long speciesId;

  private List<PlantNetCandidateDto> plantNetCandidates;
  private String plantnetBestMatch;
  private String plantnetSwitchToProject;
  private Integer plantnetQuotaRemaining;

  // Phase 8.5 T8.A — per-stage status + model tracking
  private IdentificationStageStatus identificationStatus;
  private IdentificationStageStatus annotationStatus;
  private IdentificationStageStatus candidateStatus;
  private String identificationModel;
  private String annotationModel;
  private String failureReason;
  private String userContext;
}
