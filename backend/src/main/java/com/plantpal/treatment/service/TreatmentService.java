package com.plantpal.treatment.service;

import com.plantpal.treatment.dto.CreateTreatmentRequest;
import com.plantpal.treatment.dto.TreatmentResponse;
import java.util.List;
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

  /**
   * Returns every DRAFT/IN_PROGRESS treatment for the plant, newest first — unlike {@link
   * #getActiveTreatmentForPlant}, which only surfaces the single most recently created one. A plant
   * can have several diseases under treatment at once, even though {@code Plant.activeTreatmentId}
   * only ever points at one of them.
   */
  List<TreatmentResponse> getActiveTreatmentsForPlant(Long plantId, Long userId);

  TreatmentResponse completeTreatment(Long id, Long userId);

  /**
   * Dismisses (status -&gt; DISMISSED) any DRAFT/IN_PROGRESS treatment for this plant+disease.
   * Unlike the other mutating methods here, this isn't owner-scoped by a request-bound userId — the
   * caller is the background duplicate-care-card listener, not a controller, and a plant only ever
   * has one owner anyway.
   */
  void dismissActiveTreatmentForDisease(Long plantId, String diseaseName);

  /**
   * Reacts to a {@code TreatmentPlan} completing (last enabled step done) by flipping the wrapping
   * {@code Treatment}'s status to COMPLETED, mirroring {@link #completeTreatment}'s effects. No-op
   * if no Treatment wraps this plan, or if it's not currently IN_PROGRESS — covers plain ROUTINE
   * TreatmentPlans that aren't wrapped by a Treatment at all.
   */
  void syncFromTreatmentPlanCompletion(Long treatmentPlanId);
}
