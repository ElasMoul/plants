package com.plantpal.plant.dto;

import com.plantpal.plant.entity.PlantStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlantResponse {

  private Long id;

  private Long userId;

  private String nickname;

  private String species;

  private String commonName;

  private String photoUrl;

  private String location;

  private String notes;

  private PlantStatus status;

  private LocalDate acquiredAt;

  private Instant createdAt;

  private Instant updatedAt;
}
