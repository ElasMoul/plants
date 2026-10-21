package com.plantpal.plantgroup.dto;

import com.plantpal.reminder.entity.CareType;

public enum GroupCareAction {
  WATERED(CareType.WATERING),
  FED(CareType.FERTILIZING);

  private final CareType careType;

  GroupCareAction(CareType careType) {
    this.careType = careType;
  }

  public CareType careType() {
    return careType;
  }
}
