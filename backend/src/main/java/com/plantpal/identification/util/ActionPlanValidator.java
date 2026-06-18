package com.plantpal.identification.util;

import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.dto.DiagramDto;
import com.plantpal.identification.dto.TreatmentStepDto;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defensive normalization for AI-generated {@link ActionPlanDto} payloads — never throws. An action
 * plan that fails validation degrades to {@code null} (informational-only care card) rather than
 * failing the whole identification or cure-advice response.
 */
public final class ActionPlanValidator {

  private static final Logger log = LoggerFactory.getLogger(ActionPlanValidator.class);

  private static final String ROUTINE = "ROUTINE";
  private static final String TREATMENT = "TREATMENT";
  private static final String MERMAID = "MERMAID";

  private static final int MIN_FREQUENCY_DAYS = 1;
  private static final int MAX_FREQUENCY_DAYS = 365;
  private static final int MAX_STEPS = 10;
  private static final int MIN_DUE_OFFSET_DAYS = 0;
  private static final int MAX_DUE_OFFSET_DAYS = 180;
  private static final int MAX_DIAGRAM_CONTENT_LENGTH = 2000;

  private ActionPlanValidator() {}

  public static ActionPlanDto normalize(ActionPlanDto raw) {
    if (raw == null || raw.getType() == null) {
      return null;
    }
    String type = raw.getType().trim().toUpperCase();
    if (ROUTINE.equals(type)) {
      return normalizeRoutine(raw);
    }
    if (TREATMENT.equals(type)) {
      return normalizeTreatment(raw);
    }
    return null;
  }

  private static ActionPlanDto normalizeRoutine(ActionPlanDto raw) {
    Integer frequencyDays = raw.getFrequencyDays();
    if (frequencyDays == null
        || frequencyDays < MIN_FREQUENCY_DAYS
        || frequencyDays > MAX_FREQUENCY_DAYS) {
      return null;
    }
    return ActionPlanDto.builder().type(ROUTINE).frequencyDays(frequencyDays).build();
  }

  private static ActionPlanDto normalizeTreatment(ActionPlanDto raw) {
    List<TreatmentStepDto> rawSteps = raw.getSteps();
    if (rawSteps == null || rawSteps.isEmpty()) {
      return null;
    }

    List<TreatmentStepDto> truncated = rawSteps;
    if (rawSteps.size() > MAX_STEPS) {
      log.warn("Treatment plan had {} steps, truncating to {}", rawSteps.size(), MAX_STEPS);
      truncated = rawSteps.subList(0, MAX_STEPS);
    }

    List<TreatmentStepDto> normalizedSteps = new ArrayList<>();
    int order = 1;
    for (TreatmentStepDto step : truncated) {
      normalizedSteps.add(
          TreatmentStepDto.builder()
              .order(order++)
              .instruction(step.getInstruction())
              .dueOffsetDays(
                  clamp(step.getDueOffsetDays(), MIN_DUE_OFFSET_DAYS, MAX_DUE_OFFSET_DAYS))
              .build());
    }

    return ActionPlanDto.builder()
        .type(TREATMENT)
        .steps(normalizedSteps)
        .diagram(normalizeDiagram(raw.getDiagram()))
        .build();
  }

  private static DiagramDto normalizeDiagram(DiagramDto diagram) {
    if (diagram == null) {
      return null;
    }
    if (diagram.getFormat() == null || !MERMAID.equalsIgnoreCase(diagram.getFormat())) {
      return null;
    }
    if (diagram.getContent() == null || diagram.getContent().isBlank()) {
      return null;
    }
    if (diagram.getContent().length() > MAX_DIAGRAM_CONTENT_LENGTH) {
      return null;
    }
    return DiagramDto.builder().format(MERMAID).content(diagram.getContent()).build();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
