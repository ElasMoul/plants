package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import java.time.Duration;
import java.util.Base64;
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

  static final String PLANT_IDENTIFICATION_SYSTEM_PROMPT =
      """
      You are an expert botanist and plant pathologist helping a beginner gardener.
      Analyse the plant in the photo and return ONLY valid JSON (no markdown, no preamble).

      {
        "species": "<scientific name, or null if truly unidentifiable>",
        "commonName": "<common English name>",
        "confidence": "HIGH | MEDIUM | LOW",
        "healthStatus": "HEALTHY | ISSUES_DETECTED | UNKNOWN",
        "healthNotes": "<brief description of visible issues, or null if healthy>",
        "carePlan": {
          "wateringFrequencyDays": <int>,
          "fertilizingFrequencyDays": <int — 0 means never>,
          "repottingFrequencyMonths": <int>,
          "careCards": [
            {
              "type": "WATERING | LIGHT | HUMIDITY | TEMPERATURE | FERTILIZING | REPOTTING | PRUNING | PEST | SEASONAL | BEGINNER_TIP",
              "title": "<short title>",
              "icon": "<material icon name e.g. water_drop, wb_sunny, thermostat>",
              "summary": "<one sentence>",
              "detail": "<2-4 sentences, plain English, no jargon>",
              "urgency": "LOW | MEDIUM | HIGH",
              "seasonalVariation": "<what changes in winter/summer, or null>"
            }
          ],
          "beginnerWarnings": ["warning1", "warning2"]
        }
      }

      Rules:
      - Identify only the PLANT, ignoring background objects.
      - Include 4-8 care cards covering the most important aspects for this specific species.
      - If you cannot identify the plant, still provide general care advice and set confidence to LOW.
      - Write for someone who has never owned a plant before.
      """;

  static final String ANNOTATION_SYSTEM_PROMPT =
      """
      You are a computer vision system specialised in plant analysis.
      Examine the image and identify all notable regions.
      Return ONLY valid JSON (no markdown, no preamble):

      {
        "regions": [
          {
            "label": "<specific description>",
            "type": "PLANT | DISEASE | HEALTHY_AREA",
            "confidence": "HIGH | MEDIUM | LOW",
            "polygon": [
              { "xPct": <0-100>, "yPct": <0-100> },
              { "xPct": <0-100>, "yPct": <0-100> }
            ]
          }
        ]
      }

      Rules:
      - Always include at least one PLANT region for the main plant body.
      - Add DISEASE regions for yellowing, spots, wilting, pests, rot, or visible damage.
      - Add HEALTHY_AREA only for notably healthy growth worth highlighting.
      - Polygon points must trace the boundary clockwise.
      - First and last point need NOT be identical (canvas will close the path).
      - All xPct/yPct must be integers 0-100 inclusive.
      - If the region shape is simple (whole plant body), 4 corner points are enough.
      - For complex disease areas (irregular spots), use 8-16 points.
      - Use specific labels (e.g. "Monstera deliciosa", "Yellowing — possible overwatering").
      """;

  static final String CURE_ADVICE_SYSTEM_PROMPT =
      """
      You are a plant pathologist. Answer in plain English for a beginner gardener.
      Be direct and practical. Do NOT use markdown. No headers, no bullet symbols — write
      numbered steps as plain text: '1. Remove affected leaves. 2. Apply neem oil...'
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
            "seasonalVariation": "<what changes in winter/summer, or null>"
          }
        ],
        "beginnerWarnings": ["warning1", "warning2"]
      }
      Include 4-8 care cards covering the most important aspects for this specific plant.
      For rare/unusual care requirements, add extra cards. Omit irrelevant types.
      Write for someone who has never owned a plant before.
      """;

  private final RestClient restClient;
  private final String model;
  private final String visionModel;

  public DeepSeekClient(
      @Value("${deepseek.base-url:https://models.inference.ai.azure.com}") String baseUrl,
      @Value("${deepseek.api-key}") String apiKey,
      @Value("${deepseek.model:DeepSeek-R1}") String model,
      @Value("${deepseek.vision-model:gpt-4o}") String visionModel) {
    this.model = model;
    this.visionModel = visionModel;
    // No version constraint — let JDK negotiate HTTP/2 via ALPN (required by Azure/GitHub Models).
    // PlantNetClient is the one that needs HTTP_1_1; this client must NOT force it.
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofMinutes(5));
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + apiKey)
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
        throw new PlantPalException("Empty response from cure advice service", 503);
      }

      String raw = response.choices().get(0).message().content();
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

  public String identifyPlant(byte[] imageBytes, String mediaType) {
    String dataUrl =
        "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

    List<Map<String, Object>> userContent =
        List.of(
            Map.of("type", "image_url", "image_url", Map.of("url", dataUrl, "detail", "high")),
            Map.of(
                "type",
                "text",
                "text",
                "Identify this plant and generate a complete beginner care plan."));

    Map<String, Object> requestBody =
        Map.of(
            "model",
            visionModel,
            "messages",
            List.of(
                Map.of("role", "system", "content", PLANT_IDENTIFICATION_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userContent)),
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
        throw new PlantPalException("Empty response from identification service", 503);
      }

      String raw = response.choices().get(0).message().content();
      String content = stripThinkTags(raw);
      log.info(
          "DeepSeek plant identification completed in {}ms", System.currentTimeMillis() - start);
      log.debug("DeepSeek identification raw response: {}", raw);
      return content;

    } catch (RestClientResponseException e) {
      log.error(
          "DeepSeek identification error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Plant identification service unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek identification API", e);
      throw new PlantPalException("Plant identification service unavailable", 503);
    }
  }

  public String analyzeRegions(byte[] imageBytes, String mediaType) {
    String dataUrl =
        "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

    List<Map<String, Object>> userContent =
        List.of(
            Map.of("type", "image_url", "image_url", Map.of("url", dataUrl, "detail", "high")),
            Map.of("type", "text", "text", "Identify and locate all plant regions in this image."));

    Map<String, Object> requestBody =
        Map.of(
            "model",
            visionModel,
            "messages",
            List.of(
                Map.of("role", "system", "content", ANNOTATION_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userContent)),
            "temperature",
            0.2,
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
        throw new PlantPalException("Empty response from annotation service", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.info("DeepSeek annotation completed in {}ms", System.currentTimeMillis() - start);
      return stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      log.error(
          "DeepSeek annotation error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Annotation service unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach DeepSeek annotation API", e);
      throw new PlantPalException("Annotation service unavailable", 503);
    }
  }

  /**
   * R1 and other reasoning models wrap output in <think>...</think> before the JSON answer. Strip
   * that block so downstream JSON parsers receive clean content.
   */
  private String stripThinkTags(String raw) {
    if (raw == null) return null;
    int endThink = raw.indexOf("</think>");
    if (endThink != -1) {
      return raw.substring(endThink + "</think>".length()).strip();
    }
    return raw.strip();
  }

  private record DeepSeekApiResponse(List<Choice> choices) {}

  private record Choice(Message message) {}

  private record Message(String content) {}
}
