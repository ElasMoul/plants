package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Claude is multimodal, so this one client serves both {@link
 * com.plantpal.user.entity.VisionModelPreference#ANTHROPIC_CLAUDE} (identification) and {@link
 * com.plantpal.user.entity.ReasoningModelPreference#ANTHROPIC_CLAUDE} (cure advice, disease
 * description) — unlike GitHub Models, which splits vision and text across two clients.
 *
 * <p>{@code anthropic.api.key} has no required default (unlike {@code github.token}) so the
 * application still boots when no Anthropic key is configured; callers must check {@link
 * #isAvailable()} before routing to this client (see {@code UserServiceImpl}'s availability flags)
 * — an un-keyed call would otherwise surface as a confusing 401 from Anthropic.
 */
@Component
public class AnthropicClient {

  private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

  private static final String API_VERSION = "2023-06-01";
  private static final int DEFAULT_MAX_TOKENS = 4096;
  // Anthropic doesn't document a fixed wait in the error body the way GitHub Models does; fall
  // back to the same default the other clients use when retry-after isn't present as a header.
  private static final long DEFAULT_RETRY_AFTER_SECONDS = 60;

  private final RestClient restClient;
  private final String apiKey;
  private final String defaultModel;

  // anthropic.models.cheap / .max are configured (application-*.yml) but not yet wired to a
  // routing choice — ReasoningModelPreference/VisionModelPreference only expose one Claude tier
  // today. Reserved for a future "fast vs frontier" picker rather than read here.
  public AnthropicClient(
      @Value("${anthropic.base-url:https://api.anthropic.com}") String baseUrl,
      @Value("${anthropic.api.key:}") String apiKey,
      @Value("${anthropic.models.default:claude-sonnet-4-6}") String defaultModel) {
    this.apiKey = apiKey;
    this.defaultModel = defaultModel;
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(30))
            .setResponseTimeout(Timeout.ofMinutes(5))
            .build();
    CloseableHttpClient httpClient =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", API_VERSION)
            .build();
  }

  /** True once an API key is configured — used to gate this option as selectable/unselectable. */
  public boolean isAvailable() {
    return apiKey != null && !apiKey.isBlank();
  }

  /**
   * Configured model string for {@code *ModelPreference.ANTHROPIC_CLAUDE} (D022 gateway swap —
   * modelHint).
   */
  public String getDefaultModel() {
    return defaultModel;
  }

  public String identifyPlant(byte[] imageBytes, String mediaType) {
    return identifyPlant(imageBytes, mediaType, null);
  }

  public String identifyPlant(byte[] imageBytes, String mediaType, String userContext) {
    String basePrompt = "Identify this plant and generate a complete beginner care plan.";
    String promptText =
        (userContext != null && !userContext.isBlank())
            ? basePrompt
                + " The user wants to know: "
                + userContext
                + ". Consider this when assessing health and generating care advice"
                + " — address their specific concern directly."
            : basePrompt;
    List<Map<String, Object>> userContent =
        List.of(imageBlock(imageBytes, mediaType), Map.of("type", "text", "text", promptText));
    return call(
        defaultModel,
        GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT,
        userContent,
        "Plant identification");
  }

  public String analyzeRegions(byte[] imageBytes, String mediaType) {
    List<Map<String, Object>> userContent =
        List.of(
            imageBlock(imageBytes, mediaType),
            Map.of("type", "text", "text", "Identify and locate all plant regions in this image."));
    return call(
        defaultModel, GitHubModelsClient.ANNOTATION_SYSTEM_PROMPT, userContent, "Annotation");
  }

  public String generateCureAdvice(String species, String regionLabel) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage =
        "My "
            + effectiveSpecies
            + " has the following issue: "
            + regionLabel
            + ". Provide a concise cure procedure in 3-5 numbered steps.";
    return call(
        defaultModel,
        DeepSeekClient.CURE_ADVICE_SYSTEM_PROMPT,
        List.of(Map.of("type", "text", "text", userMessage)),
        "Cure advice");
  }

  public String generateDiseaseDescription(String species, String diseaseName) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage = "Plant: " + effectiveSpecies + "\nDisease/pest issue: " + diseaseName;
    return call(
        defaultModel,
        DeepSeekClient.DISEASE_DESCRIPTION_SYSTEM_PROMPT,
        List.of(Map.of("type", "text", "text", userMessage)),
        "Disease description");
  }

  /** Buffered chat turn — systemPrompt carries garden context + history, same as gatewayChat. */
  public String chat(String systemPrompt, String userMessage) {
    return call(
        defaultModel, systemPrompt, List.of(Map.of("type", "text", "text", userMessage)), "Chat");
  }

  public String generateSpeciesEnrichment(String scientificName, String commonName) {
    String userMessage =
        "Scientific name: "
            + scientificName
            + (commonName != null ? "\nCommon name: " + commonName : "");
    return call(
        defaultModel,
        DeepSeekClient.SPECIES_ENRICHMENT_SYSTEM_PROMPT,
        List.of(Map.of("type", "text", "text", userMessage)),
        "Species enrichment");
  }

  private Map<String, Object> imageBlock(byte[] imageBytes, String mediaType) {
    return Map.of(
        "type",
        "image",
        "source",
        Map.of(
            "type",
            "base64",
            "media_type",
            mediaType,
            "data",
            Base64.getEncoder().encodeToString(imageBytes)));
  }

  private String call(String requestModel, String systemPrompt, Object userContent, String label) {
    if (!isAvailable()) {
      throw new PlantPalException(
          label + " unavailable — Anthropic API key is not configured", 503);
    }

    Map<String, Object> requestBody =
        Map.of(
            "model",
            requestModel,
            "max_tokens",
            DEFAULT_MAX_TOKENS,
            "system",
            systemPrompt,
            "messages",
            List.of(Map.of("role", "user", "content", userContent)));

    long start = System.currentTimeMillis();
    try {
      AnthropicApiResponse response =
          restClient
              .post()
              .uri("/v1/messages")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(AnthropicApiResponse.class);

      if (response == null || response.content() == null || response.content().isEmpty()) {
        throw new PlantPalException("Empty response from " + label.toLowerCase() + " service", 503);
      }

      String raw =
          response.content().stream()
              .filter(block -> "text".equals(block.type()) && block.text() != null)
              .map(AnthropicContentBlock::text)
              .collect(Collectors.joining());
      log.info(
          "{} via Anthropic completed in {}ms [model={}]",
          label,
          System.currentTimeMillis() - start,
          requestModel);
      log.debug("Anthropic {} raw response: {}", label, raw);
      return DeepSeekClient.stripThinkTags(raw);

    } catch (RestClientResponseException e) {
      throw toServiceException(label + " unavailable", e);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach Anthropic API for {}", label, e);
      throw new PlantPalException(label + " unavailable", 503);
    }
  }

  private PlantPalException toServiceException(
      String fallbackMessage, RestClientResponseException e) {
    log.error(
        "Anthropic error status={}, body={}",
        e.getStatusCode().value(),
        e.getResponseBodyAsString());
    if (e.getStatusCode().value() == 429) {
      return new RateLimitException(
          "Anthropic rate limit reached — try again later", extractRetryAfterSeconds(e));
    }
    return new PlantPalException(fallbackMessage, 503);
  }

  private static long extractRetryAfterSeconds(RestClientResponseException e) {
    String header =
        e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("retry-after") : null;
    if (header != null) {
      try {
        return Long.parseLong(header.trim());
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }
    return DEFAULT_RETRY_AFTER_SECONDS;
  }

  private record AnthropicApiResponse(List<AnthropicContentBlock> content) {}

  private record AnthropicContentBlock(String type, String text) {}
}
