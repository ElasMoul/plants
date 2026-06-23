package com.plantpal.identification.dto;

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
public class PlantNetReferenceImageDto {
  private String smallUrl;
  private String mediumUrl;
  private String author;
  private String license;
  private String citation;
}
