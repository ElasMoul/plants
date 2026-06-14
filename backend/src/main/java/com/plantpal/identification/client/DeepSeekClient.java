package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import java.net.http.HttpClient;
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
      @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl,
      @Value("${deepseek.api-key}") String apiKey,
      @Value("${deepseek.model:deepseek-chat}") String model,
      @Value("${deepseek.vision-model:deepseek-chat}") String visionModel) {
    this.model = model;
    this.visionModel = visionModel;
    // Force HTTP/1.1 — same pattern as PlantNetClient to avoid ALPN negotiation issues.
    HttpClient http1Client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new JdkClientHttpRequestFactory(http1Client))
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
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

      log.info(
          "DeepSeek care plan generated in {}ms for species={}",
          System.currentTimeMillis() - start,
          species);
      return response.choices().get(0).message().content();

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

      log.info(
          "DeepSeek plant identification completed in {}ms", System.currentTimeMillis() - start);
      return response.choices().get(0).message().content();

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

  private record DeepSeekApiResponse(List<Choice> choices) {}

  private record Choice(Message message) {}

  private record Message(String content) {}
}
