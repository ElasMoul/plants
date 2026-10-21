package com.plantpal.plantgroup.dto;

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
public class GroupCareRequest {

  @NotNull private GroupCareAction action;

  @Size(max = 1000)
  private String notes;
}
