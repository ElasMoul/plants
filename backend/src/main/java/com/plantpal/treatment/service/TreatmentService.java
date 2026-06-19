package com.plantpal.treatment.service;

import com.plantpal.treatment.dto.CreateTreatmentRequest;
import com.plantpal.treatment.dto.TreatmentResponse;
import java.util.concurrent.CompletableFuture;

public interface TreatmentService {

  TreatmentResponse createTreatment(CreateTreatmentRequest request, Long userId);

  /**
   * Generates the treatment's TREATMENT-type action plan via AI and delegates reminder creation to
   * {@code TreatmentPlanService.createFromActionPlan()}. Wraps an AI call (5-15s) — never called
   * directly from a controller thread, hence {@code CompletableFuture}.
   */
  CompletableFuture<TreatmentResponse> craftPlan(Long id, Long userId);

  TreatmentResponse getTreatment(Long id, Long userId);

  TreatmentResponse getActiveTreatmentForPlant(Long plantId, Long userId);

  TreatmentResponse completeTreatment(Long id, Long userId);
}
