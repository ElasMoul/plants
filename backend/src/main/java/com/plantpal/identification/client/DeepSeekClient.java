package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DeepSeekClient {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

  static final String CURE_ADVICE_SYSTEM_PROMPT =
      """
      You are a plant pathologist. Answer in plain English for a beginner gardener.
      Be direct and practical. Return ONLY valid JSON (no markdown, no preamble):

      {
        "advice": "<plain text, numbered steps as plain text — no markdown, no headers, no bullet"
                   + " symbols: '1. Remove affected leaves. 2. Apply neem oil...'>",
        "actionPlan": {
          "type": "ROUTINE | TREATMENT | null — null if this is a single one-off tip",
          "frequencyDays": "<int, ROUTINE only>",
          "steps": [
            {
              "order": <int>,
              "instruction": "<string>",
              "dueOffsetDays": <int>,
              "detail": "<2-4 sentences of substeps/precautions for this specific step, or null>",
              "diagram": { "format": "MERMAID", "content": "<mermaid flowchart syntax>" } or null
            }
          ],
          "diagram": { "format": "MERMAID", "content": "<mermaid flowchart syntax>" } or null
        }
      }

      Only set actionPlan.type=TREATMENT for genuinely multi-step processes (3+ distinct actions
      over time) — a single one-off tip should have actionPlan: null.
      Only include a plan-level diagram when the steps have real branching/decision logic worth
      visualising — most linear step lists do not need one.
      Only fill a step's own "detail" when that step needs more than the one-line instruction —
      e.g. a mixing ratio, a multi-part procedure, or precautions to take. Leave it null for
      simple steps.
      Only give a step its own "diagram" when that single step has a multi-part procedure or its
      own decision branching worth visualising (e.g. "mix solution → test on one leaf → no
      reaction? apply fully : dilute further"). This is rare — most steps should have
      diagram: null.
      Mermaid diagrams must use only "flowchart LR" or "flowchart TD". Never use subgraph, click
      events, or style blocks. Node labels must not contain double quotes - use single quotes or
      backticks only.
      """;

  static final String CARE_PLAN_SYSTEM_PROMPT =
      """
      You are an expert botanist and horticulturist helping a beginner gardener.
      Given a plant species, generate a complete, beginner-friendly care plan.
      Return ONLY valid JSON (no markdown). Structure:
      {
        "wateringFrequencyDays": <int — how often to water in summer>,
        "fertilizingFrequencyDays": <int — 0 means never>,
        "repottingFrequencyMonths": <int>,
        "careCards": [
          {
            "type": "WATERING | LIGHT | HUMIDITY | TEMPERATURE | FERTILIZING | REPOTTING | PRUNING | PEST | SEASONAL | BEGINNER_TIP",
            "title": "<short title>",
            "icon": "<material icon name, e.g. water_drop, wb_sunny, thermostat>",
            "summary": "<one sentence, e.g. Water every 7 days>",
            "detail": "<2-4 sentences, plain English, no jargon>",
            "urgency": "LOW | MEDIUM | HIGH",
            "seasonalVariation": "<what changes in winter/summer, or null>",
            "actionPlan": {
              "type": "ROUTINE | TREATMENT | null — null if this card is purely informational",
              "frequencyDays": "<int, ROUTINE only>",
              "steps": [
                {
                  "order": <int>,
                  "instruction": "<string>",
                  "dueOffsetDays": <int>,
                  "detail": "<2-4 sentences of substeps/precautions for this specific step, or null>",
                  "diagram": { "format": "MERMAID", "content": "<mermaid flowchart syntax>" } or null
                }
              ],
              "diagram": { "format": "MERMAID", "content": "<mermaid flowchart syntax>" } or null
            }
          }
        ],
        "beginnerWarnings": ["warning1", "warning2"]
      }
      Include 4-8 care cards covering the most important aspects for this specific plant.
      For rare/unusual care requirements, add extra cards. Omit irrelevant types.
      Write for someone who has never owned a plant before.
      Only set actionPlan.type=TREATMENT for genuinely multi-step processes (3+ distinct actions
      over time) — a single one-off tip should have actionPlan: null.
      Only include a plan-level diagram when the steps have real branching/decision logic worth
      visualising — most linear step lists do not need one.
      Only fill a step's own "detail" when that step needs more than the one-line instruction —
      e.g. a mixing ratio, a multi-part procedure, or precautions to take. Leave it null for
      simple steps.
      Only give a step its own "diagram" when that single step has a multi-part procedure or its
      own decision branching worth visualising (e.g. "mix solution → test on one leaf → no
      reaction? apply fully : dilute further"). This is rare — most steps should have
      diagram: null.
      actionPlan must be null for card types: LIGHT, HUMIDITY, TEMPERATURE, SEASONAL. These are
      environmental conditions, never ROUTINE or TREATMENT plans.
      ROUTINE actionPlan is valid only for: WATERING, FERTILIZING, REPOTTING, PRUNING.
      TREATMENT actionPlan is valid only for: PEST, and WATERING/FERTILIZING when issues are
      detected.
      Mermaid diagrams must use only "flowchart LR" or "flowchart TD". Never use subgraph, click
      events, or style blocks. Node labels must not contain double quotes - use single quotes or
      backticks only.
      """;

  static final String DISEASE_DESCRIPTION_SYSTEM_PROMPT =
      """
      You are a plant pathologist. Explain a plant disease or pest issue in plain English for a
      beginner gardener. In 2-4 sentences, cover what it is, why it happens, and the risk if left
      untreated. Return ONLY the plain text explanation — no markdown, no headers, no JSON.
      """;

  static final String SPECIES_ENRICHMENT_SYSTEM_PROMPT =
      """
      You are an expert botanist. Given a plant's scientific name (and optionally a common name),
      return ONLY valid JSON (no markdown, no preamble) describing it for a beginner gardener:
      {
        "description": "<2-4 sentences: what the plant is, its origin, notable characteristics>",
        "careOverview": "<1-2 sentence summary of its general care needs>",
        "imageUrl": "<a representative public photo URL for this species, or null if unsure>",
        "careCards": [
          {
            "type": "<one of: WATERING, LIGHT, HUMIDITY, TEMPERATURE, FERTILIZING, REPOTTING, PRUNING, BEGINNER_TIP>",
            "title": "<short card title>",
            "icon": "<a single Material Symbols icon name, e.g. water_drop>",
            "summary": "<one sentence, shown collapsed>",
            "detail": "<2-4 sentences of fuller guidance, shown expanded>",
            "urgency": "<LOW | MEDIUM | HIGH>",
            "seasonalVariation": "<one sentence on how this changes by season, or null>"
          }
        ],
        "source": "AI"
      }
      careCards is general care guidance for the SPECIES as a whole (not tied to any one plant's
      health or a specific issue) -- 3 to 6 cards covering the topics most relevant to this
      species. Never include a PEST or SEASONAL card here, and never include an "actionPlan" field
      on any card -- those require a specific plant's reminders, which don't exist at the species
      level. If you are not confident about a field, return null for that field rather than
      guessing; if you have nothing reliable to say, return an empty careCards array.
      """;

  static final String DUPLICATE_CARE_CARDS_SYSTEM_PROMPT =
      """
      You review a plant's list of "care cards" — each one a detected disease/pest issue that was
      added to the plant's care plan from a separate scan. Find duplicates: multiple cards that
      describe the SAME underlying problem, even if worded differently (e.g. "Leaf yellowing" and
      "Chlorosis on leaves" are likely the same issue; "Spider mites" and "Powdery mildew" are NOT
      the same issue — different problems stay separate).

      Each card has a "ref" (its id), "title", and "detail". Return ONLY valid JSON (no markdown,
      no preamble):
      {
        "duplicateGroups": [["ref1", "ref2"], ...]
      }
      Only include groups of 2 or more refs that genuinely describe the same problem. If there are
      no duplicates, return {"duplicateGroups": []}.
      """;

  private final RestClient restClient;
  private final String model;

  public DeepSeekClient(
      @Value("${github.base-url:https://models.inference.ai.azure.com}") String baseUrl,
      @Value("${github.token}") String token,
      @Value("${deepseek.model:DeepSeek-R1}") String model) {
    this.model = model;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofMinutes(5));
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
  }

  public String generateCureAdvice(String species, String regionLabel) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage =
        "My "
            + effectiveSpecies
            + " has the following issue: "
            + regionLabel
            + ". Provide a concise cure procedure in 3-5 numbered steps.";

    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of("role", "system", "content", CURE_ADVICE_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userMessage)),
            "temperature",
            0.3,
            "response_format",
            Map.of("type", "json_object"));

    long start = System.currentTimeMillis();
    try {
      DeepSeekApiResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(DeepSeekApiResponse.class);

      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new PlantPalException("Empty response from cure advice service", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.debug("DeepSeek cure advice raw response: {}", raw);
      log.info("DeepSeek cure advice generated in {}ms", System.currentTimeMillis() - start);
      return stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      log.error(
          "DeepSeek cure advice error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Cure advice unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek cure advice API", e);
      throw new PlantPalException("Cure advice unavailable", 503);
    }
  }

  public String generateDiseaseDescription(String species, String diseaseName) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage = "Plant: " + effectiveSpecies + "\nDisease/pest issue: " + diseaseName;

    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of("role", "system", "content", DISEASE_DESCRIPTION_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userMessage)),
            "temperature",
            0.3);

    long start = System.currentTimeMillis();
    try {
      DeepSeekApiResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(DeepSeekApiResponse.class);

      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new PlantPalException("Empty response from disease description service", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.debug("DeepSeek disease description raw response: {}", raw);
      log.info(
          "DeepSeek disease description generated in {}ms for diseaseName={}",
          System.currentTimeMillis() - start,
          diseaseName);
      return stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      log.error(
          "DeepSeek disease description error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Disease description unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek disease description API", e);
      throw new PlantPalException("Disease description unavailable", 503);
    }
  }

  public String generateSpeciesEnrichment(String scientificName, String commonName) {
    String userMessage =
        "Scientific name: "
            + scientificName
            + (commonName != null ? "\nCommon name: " + commonName : "");

    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of("role", "system", "content", SPECIES_ENRICHMENT_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userMessage)),
            "temperature",
            0.3,
            "response_format",
            Map.of("type", "json_object"));

    long start = System.currentTimeMillis();
    try {
      DeepSeekApiResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(DeepSeekApiResponse.class);

      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new PlantPalException("Empty response from species enrichment service", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.debug("DeepSeek species enrichment raw response: {}", raw);
      log.info(
          "DeepSeek species enrichment generated in {}ms for scientificName={}",
          System.currentTimeMillis() - start,
          scientificName);
      return stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      log.error(
          "DeepSeek species enrichment error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Species enrichment unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek species enrichment API", e);
      throw new PlantPalException("Species enrichment unavailable", 503);
    }
  }

  public String generateCarePlan(String species, String commonName, String healthNotes) {
    String userMessage =
        "Plant: "
            + species
            + " ("
            + (commonName != null ? commonName : species)
            + ")\nHealth notes: "
            + (healthNotes != null ? healthNotes : "No issues noted");

    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of("role", "system", "content", CARE_PLAN_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userMessage)),
            "temperature",
            0.3,
            "response_format",
            Map.of("type", "json_object"));

    long start = System.currentTimeMillis();
    try {
      DeepSeekApiResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(DeepSeekApiResponse.class);

      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new PlantPalException("Empty response from care plan service", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.debug("DeepSeek care plan raw response: {}", raw);
      log.info(
          "DeepSeek care plan generated in {}ms for species={}",
          System.currentTimeMillis() - start,
          species);
      return stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      log.error(
          "DeepSeek returned error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Care plan service unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek API", e);
      throw new PlantPalException("Care plan service unavailable", 503);
    }
  }

  public String detectDuplicateCareCards(String cardsJson) {
    String userMessage = "Care cards:\n" + cardsJson;

    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of("role", "system", "content", DUPLICATE_CARE_CARDS_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userMessage)),
            "temperature",
            0.1,
            "response_format",
            Map.of("type", "json_object"));

    long start = System.currentTimeMillis();
    try {
      DeepSeekApiResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(DeepSeekApiResponse.class);

      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new PlantPalException("Empty response from duplicate care card check", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.debug("DeepSeek duplicate care card check raw response: {}", raw);
      log.info(
          "DeepSeek duplicate care card check completed in {}ms",
          System.currentTimeMillis() - start);
      return stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      log.error(
          "DeepSeek duplicate care card check error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Duplicate care card check unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek duplicate care card check API", e);
      throw new PlantPalException("Duplicate care card check unavailable", 503);
    }
  }

  /**
   * Strips model-specific wrappers so downstream JSON parsers receive clean content: - R1/reasoning
   * models prefix output with {@code <think>...</think>} - Vision models sometimes ignore {@code
   * response_format} and wrap JSON in {@code ```json...```}
   */
  static String stripThinkTags(String raw) {
    if (raw == null) return null;
    int endThink = raw.indexOf("</think>");
    String stripped =
        endThink != -1 ? raw.substring(endThink + "</think>".length()).strip() : raw.strip();
    if (stripped.startsWith("```")) {
      int firstNewline = stripped.indexOf('\n');
      if (firstNewline != -1) {
        stripped = stripped.substring(firstNewline + 1);
      }
      if (stripped.endsWith("```")) {
        stripped = stripped.substring(0, stripped.lastIndexOf("```")).strip();
      }
    }
    return stripped;
  }

  private record DeepSeekApiResponse(List<Choice> choices) {}

  private record Choice(Message message) {}

  private record Message(String content) {}
}
