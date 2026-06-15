package com.plantpal.identification.dto;

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
public class AnnotationRegionDto {
  private String label;
  private String type;
  private String confidence;
  private List<PolygonPointDto> polygon;
  private BoundingBoxDto boundingBox;
}
