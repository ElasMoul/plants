package com.plantpal.identification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolveSpeciesRequest {

  private boolean confirmed;

  // When set, the user explicitly selected this candidate from the ranked list (T8.2).
  // Null means "use whatever scientific name the AI identified" (backward compat).
  private String chosenScientificName;
}
