package com.plantpal.reminder.service;

import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.reminder.dto.TreatmentPlanResponse;

public interface TreatmentPlanService {

  TreatmentPlanResponse createFromActionPlan(
      Long plantId, Long userId, String title, String sourceCareCardType, ActionPlanDto actionPlan);

  TreatmentPlanResponse getTreatmentPlan(Long id, Long userId);
}
