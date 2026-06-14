package com.plantpal.identification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class BoundingBoxDto {
  @JsonProperty("xPct")
  private int xPct;

  @JsonProperty("yPct")
  private int yPct;

  private int widthPct;
  private int heightPct;
}
