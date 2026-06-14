package com.plantpal.identification.dto;

import com.plantpal.identification.dto.plantnet.PlantNetResult;
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
  private List<PlantNetResult> topResults;
  private String photoUrl;
  private Instant createdAt;
  private CarePlanDto carePlan;
}
