package com.plantpal.identification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolvePlantRequest {

  /** Null means "create a new plant". */
  private Long plantId;
}
