package com.plantpal.plantgroup.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupResponse {

  private Long id;

  private String name;

  /** Number of ACTIVE plants currently in the group. */
  private long plantCount;

  private Instant createdAt;
}
