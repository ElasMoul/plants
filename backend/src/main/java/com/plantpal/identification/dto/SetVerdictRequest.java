package com.plantpal.identification.dto;

import com.plantpal.identification.entity.IdentificationVerdict;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetVerdictRequest {

  @NotNull private IdentificationVerdict verdict;

  /** Only kept when the verdict is WRONG; feedback data, never alters the scan's species. */
  @Size(max = 255)
  private String correctSpecies;
}
