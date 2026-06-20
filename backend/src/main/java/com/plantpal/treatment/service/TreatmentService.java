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

  /**
   * Reacts to a {@code TreatmentPlan} completing (last enabled step done) by flipping the wrapping
   * {@code Treatment}'s status to COMPLETED, mirroring {@link #completeTreatment}'s effects. No-op
   * if no Treatment wraps this plan, or if it's not currently IN_PROGRESS — covers plain ROUTINE
   * TreatmentPlans that aren't wrapped by a Treatment at all.
   */
  void syncFromTreatmentPlanCompletion(Long treatmentPlanId);
}
