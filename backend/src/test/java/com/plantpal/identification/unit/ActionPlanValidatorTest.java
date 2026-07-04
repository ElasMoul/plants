package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.identification.dto.ActionPlanDto;
import com.plantpal.identification.dto.DiagramDto;
import com.plantpal.identification.dto.TreatmentStepDto;
import com.plantpal.identification.util.ActionPlanValidator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ActionPlanValidator — Unit Tests")
class ActionPlanValidatorTest {

  @Nested
  @DisplayName("normalize() — top-level rejection")
  class TopLevelRejection {

    @Test
    @DisplayName("should return null when the raw action plan is null")
    void shouldReturnNullForNullInput() {
      assertThat(ActionPlanValidator.normalize(null)).isNull();
    }

    @Test
    @DisplayName("should return null when type is null")
    void shouldReturnNullWhenTypeIsNull() {
      ActionPlanDto raw = ActionPlanDto.builder().type(null).frequencyDays(7).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("should return null for an unrecognised type")
    void shouldReturnNullForUnknownType() {
      ActionPlanDto raw = ActionPlanDto.builder().type("MAINTENANCE").frequencyDays(7).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("should match type case-insensitively")
    void shouldMatchTypeCaseInsensitively() {
      ActionPlanDto raw = ActionPlanDto.builder().type("routine").frequencyDays(7).build();
      ActionPlanDto result = ActionPlanValidator.normalize(raw);
      assertThat(result).isNotNull();
      assertThat(result.getType()).isEqualTo("ROUTINE");
    }
  }

  @Nested
  @DisplayName("normalize() — ROUTINE")
  class RoutineValidation {

    @Test
    @DisplayName("should accept a valid frequencyDays")
    void shouldAcceptValidFrequency() {
      ActionPlanDto raw = ActionPlanDto.builder().type("ROUTINE").frequencyDays(7).build();
      ActionPlanDto result = ActionPlanValidator.normalize(raw);
      assertThat(result).isNotNull();
      assertThat(result.getType()).isEqualTo("ROUTINE");
      assertThat(result.getFrequencyDays()).isEqualTo(7);
      assertThat(result.getSteps()).isNull();
      assertThat(result.getDiagram()).isNull();
    }

    @Test
    @DisplayName("should accept the lower boundary of 1 day")
    void shouldAcceptLowerBoundary() {
      ActionPlanDto raw = ActionPlanDto.builder().type("ROUTINE").frequencyDays(1).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNotNull();
    }

    @Test
    @DisplayName("should accept the upper boundary of 365 days")
    void shouldAcceptUpperBoundary() {
      ActionPlanDto raw = ActionPlanDto.builder().type("ROUTINE").frequencyDays(365).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNotNull();
    }

    @Test
    @DisplayName("should reject a null frequencyDays")
    void shouldRejectNullFrequency() {
      ActionPlanDto raw = ActionPlanDto.builder().type("ROUTINE").frequencyDays(null).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("should reject frequencyDays below 1")
    void shouldRejectFrequencyBelowMin() {
      ActionPlanDto raw = ActionPlanDto.builder().type("ROUTINE").frequencyDays(0).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("should reject frequencyDays above 365")
    void shouldRejectFrequencyAboveMax() {
      ActionPlanDto raw = ActionPlanDto.builder().type("ROUTINE").frequencyDays(366).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNull();
    }
  }

  @Nested
  @DisplayName("normalize() — TREATMENT")
  class TreatmentValidation {

    private TreatmentStepDto step(int order, int dueOffsetDays) {
      return TreatmentStepDto.builder()
          .order(order)
          .instruction("Step " + order)
          .dueOffsetDays(dueOffsetDays)
          .build();
    }

    @Test
    @DisplayName("should reject a null steps list")
    void shouldRejectNullSteps() {
      ActionPlanDto raw = ActionPlanDto.builder().type("TREATMENT").steps(null).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("should reject an empty steps list")
    void shouldRejectEmptySteps() {
      ActionPlanDto raw = ActionPlanDto.builder().type("TREATMENT").steps(List.of()).build();
      assertThat(ActionPlanValidator.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("should re-number steps sequentially 1..N regardless of AI-supplied order")
    void shouldRenumberStepsSequentially() {
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(5, 0), step(5, 3), step(1, 7)))
              .build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result).isNotNull();
      assertThat(result.getSteps()).hasSize(3);
      assertThat(result.getSteps().get(0).getOrder()).isEqualTo(1);
      assertThat(result.getSteps().get(1).getOrder()).isEqualTo(2);
      assertThat(result.getSteps().get(2).getOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("should truncate to the first 10 steps when more are supplied")
    void shouldTruncateToTenSteps() {
      List<TreatmentStepDto> rawSteps =
          java.util.stream.IntStream.rangeClosed(1, 15).mapToObj(i -> step(i, 0)).toList();
      ActionPlanDto raw = ActionPlanDto.builder().type("TREATMENT").steps(rawSteps).build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result).isNotNull();
      assertThat(result.getSteps()).hasSize(10);
      assertThat(result.getSteps().get(9).getOrder()).isEqualTo(10);
    }

    @Test
    @DisplayName("should clamp a negative dueOffsetDays to 0")
    void shouldClampNegativeDueOffset() {
      ActionPlanDto raw =
          ActionPlanDto.builder().type("TREATMENT").steps(List.of(step(1, -5))).build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result.getSteps().get(0).getDueOffsetDays()).isZero();
    }

    @Test
    @DisplayName("should clamp a dueOffsetDays above 180 down to 180")
    void shouldClampExcessiveDueOffset() {
      ActionPlanDto raw =
          ActionPlanDto.builder().type("TREATMENT").steps(List.of(step(1, 999))).build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result.getSteps().get(0).getDueOffsetDays()).isEqualTo(180);
    }

    @Test
    @DisplayName("should keep a valid MERMAID diagram")
    void shouldKeepValidDiagram() {
      DiagramDto diagram =
          DiagramDto.builder().format("MERMAID").content("graph TD; A-->B;").build();
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(diagram)
              .build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result.getDiagram()).isNotNull();
      assertThat(result.getDiagram().getFormat()).isEqualTo("MERMAID");
      assertThat(result.getDiagram().getContent()).isEqualTo("graph TD; A-->B;");
    }

    @Test
    @DisplayName("should match diagram format case-insensitively")
    void shouldMatchDiagramFormatCaseInsensitively() {
      DiagramDto diagram =
          DiagramDto.builder().format("mermaid").content("graph TD; A-->B;").build();
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(diagram)
              .build();

      assertThat(ActionPlanValidator.normalize(raw).getDiagram()).isNotNull();
    }

    @Test
    @DisplayName("should null out a non-MERMAID diagram while keeping the rest of the plan")
    void shouldNullOutNonMermaidDiagram() {
      DiagramDto diagram = DiagramDto.builder().format("PLANTUML").content("@startuml").build();
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(diagram)
              .build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result).isNotNull();
      assertThat(result.getDiagram()).isNull();
      assertThat(result.getSteps()).hasSize(1);
    }

    @Test
    @DisplayName("should null out a blank diagram content")
    void shouldNullOutBlankDiagramContent() {
      DiagramDto diagram = DiagramDto.builder().format("MERMAID").content("   ").build();
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(diagram)
              .build();

      assertThat(ActionPlanValidator.normalize(raw).getDiagram()).isNull();
    }

    @Test
    @DisplayName("should null out an oversized diagram content (> 2000 chars)")
    void shouldNullOutOversizedDiagramContent() {
      String oversized = "a".repeat(2001);
      DiagramDto diagram = DiagramDto.builder().format("MERMAID").content(oversized).build();
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(diagram)
              .build();

      assertThat(ActionPlanValidator.normalize(raw).getDiagram()).isNull();
    }

    @Test
    @DisplayName("should keep a diagram content exactly at the 2000 char boundary")
    void shouldKeepDiagramAtExactBoundary() {
      String exactlyMax = "a".repeat(2000);
      DiagramDto diagram = DiagramDto.builder().format("MERMAID").content(exactlyMax).build();
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(diagram)
              .build();

      assertThat(ActionPlanValidator.normalize(raw).getDiagram()).isNotNull();
    }

    @Test
    @DisplayName("should leave diagram null when none was supplied")
    void shouldHandleNullDiagram() {
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(step(1, 0)))
              .diagram(null)
              .build();

      assertThat(ActionPlanValidator.normalize(raw).getDiagram()).isNull();
    }
  }

  @Nested
  @DisplayName("normalize() — TREATMENT step detail + per-step diagram")
  class StepDetailAndDiagram {

    private TreatmentStepDto stepWithDetail(String detail) {
      return TreatmentStepDto.builder()
          .order(1)
          .instruction("Mix neem oil solution")
          .dueOffsetDays(0)
          .detail(detail)
          .build();
    }

    @Test
    @DisplayName("should keep a normal-length step detail")
    void shouldKeepNormalStepDetail() {
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(stepWithDetail("Mix 1 part neem oil with 10 parts water.")))
              .build();

      assertThat(ActionPlanValidator.normalize(raw).getSteps().get(0).getDetail())
          .isEqualTo("Mix 1 part neem oil with 10 parts water.");
    }

    @Test
    @DisplayName("should null out a blank step detail")
    void shouldNullOutBlankStepDetail() {
      ActionPlanDto raw =
          ActionPlanDto.builder().type("TREATMENT").steps(List.of(stepWithDetail("   "))).build();

      assertThat(ActionPlanValidator.normalize(raw).getSteps().get(0).getDetail()).isNull();
    }

    @Test
    @DisplayName("should leave step detail null when none was supplied")
    void shouldHandleNullStepDetail() {
      ActionPlanDto raw =
          ActionPlanDto.builder().type("TREATMENT").steps(List.of(stepWithDetail(null))).build();

      assertThat(ActionPlanValidator.normalize(raw).getSteps().get(0).getDetail()).isNull();
    }

    @Test
    @DisplayName("should truncate a step detail longer than 1000 chars")
    void shouldTruncateOversizedStepDetail() {
      String oversized = "a".repeat(1500);
      ActionPlanDto raw =
          ActionPlanDto.builder()
              .type("TREATMENT")
              .steps(List.of(stepWithDetail(oversized)))
              .build();

      assertThat(ActionPlanValidator.normalize(raw).getSteps().get(0).getDetail()).hasSize(1000);
    }

    @Test
    @DisplayName("should keep a valid per-step MERMAID diagram")
    void shouldKeepValidStepDiagram() {
      TreatmentStepDto step =
          TreatmentStepDto.builder()
              .order(1)
              .instruction("Mix solution")
              .dueOffsetDays(0)
              .diagram(DiagramDto.builder().format("MERMAID").content("graph TD; A-->B;").build())
              .build();
      ActionPlanDto raw = ActionPlanDto.builder().type("TREATMENT").steps(List.of(step)).build();

      DiagramDto result = ActionPlanValidator.normalize(raw).getSteps().get(0).getDiagram();

      assertThat(result).isNotNull();
      assertThat(result.getFormat()).isEqualTo("MERMAID");
    }

    @Test
    @DisplayName("should null out a non-MERMAID per-step diagram while keeping the step")
    void shouldNullOutNonMermaidStepDiagram() {
      TreatmentStepDto step =
          TreatmentStepDto.builder()
              .order(1)
              .instruction("Mix solution")
              .dueOffsetDays(0)
              .diagram(DiagramDto.builder().format("PLANTUML").content("@startuml").build())
              .build();
      ActionPlanDto raw = ActionPlanDto.builder().type("TREATMENT").steps(List.of(step)).build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result.getSteps().get(0).getDiagram()).isNull();
      assertThat(result.getSteps().get(0).getInstruction()).isEqualTo("Mix solution");
    }

    @Test
    @DisplayName("per-step diagram is independent from the plan-level diagram")
    void stepDiagramIsIndependentFromPlanDiagram() {
      TreatmentStepDto step =
          TreatmentStepDto.builder()
              .order(1)
              .instruction("Mix solution")
              .dueOffsetDays(0)
              .diagram(DiagramDto.builder().format("MERMAID").content("graph TD; A-->B;").build())
              .build();
      ActionPlanDto raw =
          ActionPlanDto.builder().type("TREATMENT").steps(List.of(step)).diagram(null).build();

      ActionPlanDto result = ActionPlanValidator.normalize(raw);

      assertThat(result.getDiagram()).isNull();
      assertThat(result.getSteps().get(0).getDiagram()).isNotNull();
    }
  }
}
