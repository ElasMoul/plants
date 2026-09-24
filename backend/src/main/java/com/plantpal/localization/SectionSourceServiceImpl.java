package com.plantpal.localization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.service.IdentificationService;
import com.plantpal.plant.service.PlantService;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.TreatmentPlanService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.species.service.SpeciesService;
import com.plantpal.treatment.service.TreatmentService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Resolve section text from authorized domain data, never from a client-supplied prompt. */
@Service
public class SectionSourceServiceImpl implements SectionSourceService {
  private final IdentificationService scans;
  private final SpeciesService species;
  private final TreatmentService treatments;
  private final TreatmentPlanService plans;
  private final ReminderRepository reminders;
  private final PlantService plants;
  private final ObjectMapper mapper;
  private final GeneratedAdviceService advice;

  public SectionSourceServiceImpl(
      IdentificationService scans,
      SpeciesService species,
      TreatmentService treatments,
      TreatmentPlanService plans,
      ReminderRepository reminders,
      PlantService plants,
      ObjectMapper mapper,
      GeneratedAdviceService advice) {
    this.advice = advice;
    this.scans = scans;
    this.species = species;
    this.treatments = treatments;
    this.plans = plans;
    this.reminders = reminders;
    this.plants = plants;
    this.mapper = mapper;
  }

  @Override
  public String generationLanguage(String kind, Long id, Long userId) {
    if (!"scan".equals(kind)) return "legacy";
    String language = scans.getIdentification(id, userId).getContentLanguage();
    return language == null ? "legacy" : language;
  }

  @Override
  public Map<String, JsonNode> sections(String kind, Long id, Long userId) {
    Object data =
        switch (kind) {
          case "advice" -> advice.get(id, userId);
          case "scan" -> scans.getIdentification(id, userId);
          case "species" -> species.getSpecies(id);
          case "treatment" -> treatments.getTreatment(id, userId);
          case "plan" -> plans.getTreatmentPlan(id, userId);
          case "step" ->
              reminders
                  .findByIdAndUserId(id, userId)
                  .orElseThrow(() -> new ResourceNotFoundException("Step not found"));
          case "plant" -> plants.getPlant(id, userId);
          default -> throw new ResourceNotFoundException("Section not found");
        };
    JsonNode root = mapper.valueToTree(data);
    Map<String, JsonNode> result = new LinkedHashMap<>();
    add(result, "advice", root, "advice", "actionPlan");
    add(result, "name", root, "commonName", "diseaseName");
    add(result, "health", root, "healthNotes", "annotationRegions");
    add(result, "description", root, "description", "diseaseDescription");
    add(result, "overview", root, "careOverview");
    add(result, "title", root, "title");
    add(result, "plan", root, "diagramContent");
    add(result, "step", root, "instruction", "stepDetail", "stepDiagramContent");
    JsonNode care = root.hasNonNull("carePlan") ? root.get("carePlan") : root;
    add(result, "warnings", care, "beginnerWarnings");
    JsonNode cards = care.path("careCards");
    for (int i = 0; i < cards.size(); i++) result.put("card-" + i, cards.get(i));
    if ("plan".equals(kind)) {
      for (JsonNode step : root.path("steps")) result.put("step-" + step.path("id").asLong(), step);
    }
    return result;
  }

  private void add(Map<String, JsonNode> result, String key, JsonNode root, String... fields) {
    var section = mapper.createObjectNode();
    for (String field : fields) if (root.hasNonNull(field)) section.set(field, root.get(field));
    if (!section.isEmpty()) result.put(key, section);
  }
}
