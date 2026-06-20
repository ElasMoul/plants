package com.plantpal.dashboard.dto;

import java.time.Instant;
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
public class RecentScanDto {

  private Long identificationId;
  private Long plantId;
  private String plantNickname;
  private String photoUrl;
  private String healthStatus;
  private Instant createdAt;
}
