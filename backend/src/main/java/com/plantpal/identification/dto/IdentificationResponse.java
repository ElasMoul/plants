package com.plantpal.identification.dto;

import com.plantpal.identification.entity.IdentificationStatus;
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
  private String scientificName;
  private String commonName;
  private Double confidence;
  private String healthStatus;
  private String healthNotes;
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
}
