package com.plantpal.plantgroup.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignGroupRequest {

  /** Target group; null removes the plant from its group. */
  private Long groupId;
}
