package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Despite the name, this client now serves multiple Azure-inference text models, not just
 * DeepSeek-R1 — {@link #generateCureAdviceViaO4Mini} and friends route the same {@code
 * /chat/completions} endpoint through o4-mini / gpt-4.1-mini for {@link
 * com.plantpal.user.entity.ReasoningModelPreference#GITHUB_O4_MINI} / {@code #GITHUB_GPT41_MINI}.
 * Not renamed to avoid a sweeping rename across every caller.
 */
@Component
public class DeepSeekClient {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

  // public: reused by com.plantpal.gateway call sites building AiRequest.context["systemPrompt"]
  // so the gateway path never restates the prompt (Chunk 3, D022 gateway swap).
  public static final String CURE_ADVICE_SYSTEM_PROMPT =
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
      Mermaid diagrams must use only "flowchart TD" (never LR). Limit to 5 nodes max.
      Node labels must be 15 characters or fewer - use abbreviations. Never use subgraph, click
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
      Mermaid diagrams must use only "flowchart TD" (never LR). Limit to 5 nodes max.
      Node labels must be 15 characters or fewer - use abbreviations. Never use subgraph, click
      events, or style blocks. Node labels must not contain double quotes - use single quotes or
      backticks only.
      """;

  public static final String DISEASE_DESCRIPTION_SYSTEM_PROMPT =
      """
      You are a plant pathologist. Explain a plant disease or pest issue in plain English for a
      beginner gardener. In 2-4 sentences, cover what it is, why it happens, and the risk if left
      untreated. Return ONLY the plain text explanation — no markdown, no headers, no JSON.
      """;

  public static final String SPECIES_ENRICHMENT_SYSTEM_PROMPT =
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

  // Upstream (GitHub Models / Azure inference) enforces a hard per-user-per-model rate cap
  // independent of our own Bucket4j limits — e.g. "Rate limit of 1 per 60s exceeded for
  // UserByModelByMinute." Used as the retry-after fallback when the error body doesn't carry a
  // parseable wait time.
  private static final long DEFAULT_RETRY_AFTER_SECONDS = 60;
  private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("wait (\\d+) seconds?");
  // o4-mini (and other o-series reasoning models) reject "temperature" and use
  // "max_completion_tokens" in place of an implicit cap — see chatCompletion().
  private static final int O4_MINI_MAX_COMPLETION_TOKENS = 4096;

  private final RestClient restClient;
  private final String model;
  private final String o4MiniModel;
  private final String gpt41MiniModel;

  public DeepSeekClient(
      @Value("${github.base-url:https://models.inference.ai.azure.com}") String baseUrl,
      @Value("${github.token}") String token,
      @Value("${deepseek.model:DeepSeek-R1}") String model,
      @Value("${github.models.o4-mini-model:o4-mini}") String o4MiniModel,
      @Value("${github.models.gpt41-mini-model:gpt-4.1-mini}") String gpt41MiniModel) {
    this.model = model;
    this.o4MiniModel = o4MiniModel;
    this.gpt41MiniModel = gpt41MiniModel;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofMinutes(5));
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
  }

  /**
   * Classifies an upstream error: 429 becomes a {@link RateLimitException} carrying a real
   * retry-after estimate (so the frontend's already-built rate-limit UX can show an accurate wait
   * time instead of a dead-end "service unavailable"), anything else keeps the existing generic 503
   * behavior.
   */
  private PlantPalException toServiceException(
      String fallbackMessage, RestClientResponseException e) {
    log.error(
        "DeepSeek error status={}, body={}",
        e.getStatusCode().value(),
        e.getResponseBodyAsString());
    if (e.getStatusCode().value() == 429) {
      return new RateLimitException(
          "AI reasoning service rate limit reached — try again later", extractRetryAfterSeconds(e));
    }
    return new PlantPalException(fallbackMessage, 503);
  }

  private static long extractRetryAfterSeconds(RestClientResponseException e) {
    String header =
        e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
    if (header != null) {
      try {
        return Long.parseLong(header.trim());
      } catch (NumberFormatException ignored) {
        // fall through to body parsing
      }
    }
    Matcher matcher = RETRY_AFTER_PATTERN.matcher(String.valueOf(e.getResponseBodyAsString()));
    return matcher.find() ? Long.parseLong(matcher.group(1)) : DEFAULT_RETRY_AFTER_SECONDS;
  }

  /**
   * Configured model string for {@code ReasoningModelPreference.DEEPSEEK_R1} (D022 gateway swap —
   * modelHint).
   */
  public String getModel() {
    return model;
  }

  /**
   * Configured model string for {@code ReasoningModelPreference.GITHUB_O4_MINI} (D022 gateway swap
   * — modelHint).
   */
  public String getO4MiniModel() {
    return o4MiniModel;
  }

  /**
   * Configured model string for {@code ReasoningModelPreference.GITHUB_GPT41_MINI} (D022 gateway
   * swap — modelHint).
   */
  public String getGpt41MiniModel() {
    return gpt41MiniModel;
  }

  public String generateCureAdvice(String species, String regionLabel) {
    return generateCureAdvice(species, regionLabel, model);
  }

  /** Same call as {@link #generateCureAdvice(String, String)} but routed through o4-mini. */
  public String generateCureAdviceViaO4Mini(String species, String regionLabel) {
    return generateCureAdvice(species, regionLabel, o4MiniModel);
  }

  /** Same call as {@link #generateCureAdvice(String, String)} but routed through gpt-4.1-mini. */
  public String generateCureAdviceViaGpt41Mini(String species, String regionLabel) {
    return generateCureAdvice(species, regionLabel, gpt41MiniModel);
  }

  private String generateCureAdvice(String species, String regionLabel, String requestModel) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage =
        "My "
            + effectiveSpecies
            + " has the following issue: "
            + regionLabel
            + ". Provide a concise cure procedure in 3-5 numbered steps.";
    return chatCompletion(
        requestModel, CURE_ADVICE_SYSTEM_PROMPT, userMessage, true, "Cure advice");
  }

  public String generateDiseaseDescription(String species, String diseaseName) {
    return generateDiseaseDescription(species, diseaseName, model);
  }

  /** Same call as {@link #generateDiseaseDescription(String, String)} but routed via o4-mini. */
  public String generateDiseaseDescriptionViaO4Mini(String species, String diseaseName) {
    return generateDiseaseDescription(species, diseaseName, o4MiniModel);
  }

  /** Same call as {@link #generateDiseaseDescription(String, String)}, via gpt-4.1-mini. */
  public String generateDiseaseDescriptionViaGpt41Mini(String species, String diseaseName) {
    return generateDiseaseDescription(species, diseaseName, gpt41MiniModel);
  }

  private String generateDiseaseDescription(
      String species, String diseaseName, String requestModel) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage = "Plant: " + effectiveSpecies + "\nDisease/pest issue: " + diseaseName;
    return chatCompletion(
        requestModel, DISEASE_DESCRIPTION_SYSTEM_PROMPT, userMessage, false, "Disease description");
  }

  /**
   * Shared single-turn {@code /chat/completions} call used by the cure-advice and
   * disease-description paths now that they can target DeepSeek-R1, o4-mini, or gpt-4.1-mini. The
   * o4-mini family rejects {@code temperature} and uses {@code max_completion_tokens} instead of an
   * implicit cap — see CLAUDE.md's "o4-mini request shape" flagged gap.
   */
  private String chatCompletion(
      String requestModel,
      String systemPrompt,
      String userMessage,
      boolean jsonMode,
      String errorLabel) {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", requestModel);
    requestBody.put(
        "messages",
        List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userMessage)));
    if (requestModel.equals(o4MiniModel)) {
      requestBody.put("max_completion_tokens", O4_MINI_MAX_COMPLETION_TOKENS);
    } else {
      requestBody.put("temperature", 0.3);
    }
    if (jsonMode) {
      requestBody.put("response_format", Map.of("type", "json_object"));
    }

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
        throw new PlantPalException(
            "Empty response from " + errorLabel.toLowerCase() + " service", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.debug("DeepSeek {} raw response [model={}]: {}", errorLabel, requestModel, raw);
      log.info(
          "DeepSeek {} generated in {}ms [model={}]",
          errorLabel,
          System.currentTimeMillis() - start,
          requestModel);
      return stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      throw toServiceException(errorLabel + " unavailable", e);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek {} API [model={}]", errorLabel, requestModel, e);
      throw new PlantPalException(errorLabel + " unavailable", 503);
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
      throw toServiceException("Species enrichment unavailable", e);
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
      throw toServiceException("Care plan service unavailable", e);
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
      throw toServiceException("Duplicate care card check unavailable", e);
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
