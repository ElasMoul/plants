package com.plantpal.identification.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Native DeepSeek API; independent of the legacy GitHub-hosted DeepSeek R1 client. */
@Component
public class DeepSeekDirectClient {
  private final RestClient client;
  private final String key;
  private final String model;

  public DeepSeekDirectClient(
      @Value("${deepseek.direct.base-url:https://api.deepseek.com}") String baseUrl,
      @Value("${DEEPSEEK_API_KEY:}") String key,
      @Value("${DEEPSEEK_DIRECT_MODEL:deepseek-flash}") String model) {
    this.key = key;
    this.model = model;
    var config =
        RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(30))
            .setResponseTimeout(Timeout.ofMinutes(5))
            .build();
    var http = HttpClients.custom().setDefaultRequestConfig(config).build();
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new HttpComponentsClientHttpRequestFactory(http))
            .defaultHeader("Authorization", "Bearer " + key)
            .build();
  }

  public boolean isAvailable() {
    return key != null && !key.isBlank();
  }

  public String getModel() {
    return model;
  }

  public String identifyPlant(byte[] bytes, String mediaType, String context) {
    return call(
        GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT,
        imageContent(
            bytes,
            mediaType,
            "Identify this plant and provide a care plan. User concern: "
                + (context == null ? "none" : context)));
  }

  public String analyzeRegions(byte[] bytes, String mediaType) {
    return call(
        GitHubModelsClient.ANNOTATION_SYSTEM_PROMPT,
        imageContent(bytes, mediaType, "Identify and locate plant regions in this image."));
  }

  public String generateCureAdvice(String species, String issue) {
    return call(
        DeepSeekClient.CURE_ADVICE_SYSTEM_PROMPT, "Plant: " + species + "\nIssue: " + issue);
  }

  public String generateDiseaseDescription(String species, String disease) {
    return call(
        DeepSeekClient.DISEASE_DESCRIPTION_SYSTEM_PROMPT,
        "Plant: " + species + "\nDisease: " + disease);
  }

  public String generateSpeciesEnrichment(String scientificName, String commonName) {
    return call(
        DeepSeekClient.SPECIES_ENRICHMENT_SYSTEM_PROMPT,
        "Scientific name: " + scientificName + "\nCommon name: " + commonName);
  }

  public String chat(String systemPrompt, String message) {
    return call(systemPrompt, message);
  }

  private List<Map<String, Object>> imageContent(byte[] bytes, String type, String text) {
    return List.of(
        Map.of("type", "text", "text", text),
        Map.of(
            "type",
            "image_url",
            "image_url",
            Map.of(
                "url", "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes))));
  }

  private String call(String system, Object content) {
    if (!isAvailable()) throw new PlantPalException("DeepSeek API key is not configured", 503);
    var body =
        Map.of(
            "model",
            model,
            "max_tokens",
            8192,
            "thinking",
            Map.of("type", "disabled"),
            "messages",
            List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", content)));
    try {
      JsonNode response =
          client
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      JsonNode choice = response == null ? null : response.path("choices").path(0);
      if (choice == null || !"stop".equals(choice.path("finish_reason").asText()))
        throw new PlantPalException(
            "DeepSeek returned an incomplete response. Please try again.", 503);
      String text = choice.path("message").path("content").asText("");
      if (text.isBlank()) throw new PlantPalException("DeepSeek returned an empty response", 503);
      return DeepSeekClient.stripThinkTags(text);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 429)
        throw new RateLimitException("DeepSeek is busy. Please try again later.", 60L);
      throw new PlantPalException(
          "DeepSeek is unavailable. Check the server's API key and account balance.", 503);
    } catch (RestClientException e) {
      throw new PlantPalException("Unable to reach DeepSeek. Please try again later.", 503);
    }
  }
}
