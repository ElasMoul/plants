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
public class GitHubModelsClient {

  private static final Logger log = LoggerFactory.getLogger(GitHubModelsClient.class);

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
              "seasonalVariation": "<what changes in winter/summer, or null>",
              "actionPlan": {
                "type": "ROUTINE | TREATMENT | null — null if this card is purely informational",
                "frequencyDays": "<int, ROUTINE only>",
                "steps": [ { "order": <int>, "instruction": "<string>", "dueOffsetDays": <int> } ],
                "diagram": { "format": "MERMAID", "content": "<mermaid flowchart syntax>" } or null
              }
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
      - Only set actionPlan.type=TREATMENT for genuinely multi-step processes (3+ distinct actions
        over time) — a single one-off tip should have actionPlan: null.
      - Only include a diagram when the steps have real branching/decision logic worth visualising
        — most linear step lists do not need one.
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

  private final RestClient restClient;
  private final String identificationModel;
  private final String annotationModel;

  public GitHubModelsClient(
      @Value("${github.base-url:https://models.inference.ai.azure.com}") String baseUrl,
      @Value("${github.token}") String token,
      @Value("${github.models.identification-model:gpt-4o}") String identificationModel,
      @Value("${github.models.annotation-model:gpt-4o-mini}") String annotationModel) {
    this.identificationModel = identificationModel;
    this.annotationModel = annotationModel;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofMinutes(5));
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
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
            identificationModel,
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
      GitHubModelsApiResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(GitHubModelsApiResponse.class);

      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new PlantPalException("Empty response from identification service", 503);
      }

      String raw = response.choices().get(0).message().content();
      String content = DeepSeekClient.stripThinkTags(raw);
      log.info(
          "GitHubModels plant identification completed in {}ms [model={}]",
          System.currentTimeMillis() - start,
          identificationModel);
      log.debug("GitHubModels identification raw response: {}", raw);
      return content;

    } catch (RestClientResponseException e) {
      log.error(
          "GitHubModels identification error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Plant identification service unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach GitHubModels identification API", e);
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
            annotationModel,
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
      GitHubModelsApiResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(GitHubModelsApiResponse.class);

      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new PlantPalException("Empty response from annotation service", 503);
      }

      String raw = response.choices().get(0).message().content();
      log.debug("GitHubModels annotation raw response: {}", raw);
      log.info(
          "GitHubModels annotation completed in {}ms [model={}]",
          System.currentTimeMillis() - start,
          annotationModel);
      return DeepSeekClient.stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      log.error(
          "GitHubModels annotation error status={}, body={}",
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      throw new PlantPalException("Annotation service unavailable", 503);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach GitHubModels annotation API", e);
      throw new PlantPalException("Annotation service unavailable", 503);
    }
  }

  private record GitHubModelsApiResponse(List<Choice> choices) {}

  private record Choice(Message message) {}

  private record Message(String content) {}
}
