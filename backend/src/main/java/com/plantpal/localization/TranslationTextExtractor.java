package com.plantpal.localization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Extract presentation prose only. Never mutate the source DTO or accept client-supplied text. */
@Component
public class TranslationTextExtractor {
  private static final Pattern DIAGRAM_LABEL =
      Pattern.compile("[A-Za-z_][\\w-]*\\s*[\\[({]([^\\]\\[{}()\\n]+)[\\])}]|\\|([^|\\n]+)\\|");
  private static final Set<String> FIELDS =
      Set.of(
          "healthNotes",
          "description",
          "careOverview",
          "diseaseName",
          "diseaseDescription",
          "advice",
          "title",
          "summary",
          "detail",
          "seasonalVariation",
          "instruction",
          "stepDetail",
          "treatmentPlanTitle",
          "beginnerWarnings",
          "label");
  private static final Set<String> EXCLUDED =
      Set.of(
          "plantnetCandidates",
          "plantNetCandidates",
          "userContext",
          "notes",
          "scientificName",
          "commonName",
          "plantNickname",
          "nickname",
          "failureReason");

  public List<String> extract(JsonNode data) {
    Set<String> texts = new LinkedHashSet<>();
    visit(data, "", texts);
    // Bound external input size even if an existing domain endpoint has a large response.
    if (texts.size() > 300 || texts.stream().mapToInt(String::length).sum() > 80000) {
      throw new IllegalArgumentException("Translation response is too large");
    }
    return texts.stream().sorted().toList();
  }

  private void visit(JsonNode node, String field, Set<String> texts) {
    if (EXCLUDED.contains(field)) return;
    if (node.isTextual()
        && Set.of("diagramContent", "stepDiagramContent", "content").contains(field)) {
      DIAGRAM_LABEL
          .matcher(node.asText())
          .results()
          .forEach(
              match -> {
                String label = (match.group(1) == null ? match.group(2) : match.group(1)).trim();
                if (label.startsWith("\"") && label.endsWith("\""))
                  label = label.substring(1, label.length() - 1);
                if (!label.isBlank()) texts.add(label);
              });
      return;
    }
    if (node.isTextual() && FIELDS.contains(field) && !node.asText().isBlank()) {
      texts.add(node.asText());
    } else if (node.isArray()) {
      node.forEach(child -> visit(child, field, texts));
    } else if (node.isObject()) {
      node.fields().forEachRemaining(entry -> visit(entry.getValue(), entry.getKey(), texts));
    }
  }
}
