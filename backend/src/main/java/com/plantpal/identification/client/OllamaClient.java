package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.util.ImageUtil;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OllamaClient {

  private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

  // llava-phi3 rejects high-res images; cap at this size before sending
  private static final int OLLAMA_MAX_IMAGE_SIDE_PX = 1024;

  private final RestClient restClient;
  private final String model;

  public OllamaClient(
      @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
      @Value("${ollama.model:llava-phi3}") String model) {
    this.model = model;
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  /**
   * Sends a single-turn prompt to Ollama and returns the assistant reply.
   *
   * @param userPrompt the prompt to send
   * @return the model's text response
   * @throws PlantPalException if Ollama is unreachable or returns an empty body
   */
  public String chat(String userPrompt) {
    log.debug("Sending prompt to Ollama [model={}]: {}", model, userPrompt);

    OllamaChatRequest request =
        new OllamaChatRequest(model, List.of(new OllamaMessage("user", userPrompt)), false);

    try {
      OllamaChatResponse response =
          restClient
              .post()
              .uri("/api/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(OllamaChatResponse.class);

      if (response == null || response.message() == null) {
        throw new PlantPalException("Empty response received from Ollama", 502);
      }

      log.debug("Ollama responded [model={}]", model);
      log.debug("Ollama responded [response={}]", response.message().content());
      return response.message().content();

    } catch (RestClientException ex) {
      log.error("Failed to reach Ollama at configured base-url [model={}]", model, ex);
      throw new PlantPalException("AI service unavailable — ensure Ollama is running locally", 503);
    }
  }

  /**
   * Local-model equivalent of {@link DeepSeekClient#generateSpeciesEnrichment}, used when the
   * triggering user's AiModelPreference is OLLAMA_LLAVA. Reuses DeepSeekClient's prompt constant
   * (same package, package-private) rather than duplicating it — the JSON schema this returns must
   * stay identical between providers since SpeciesEnrichmentServiceImpl parses both the same way.
   */
  public String generateSpeciesEnrichment(String scientificName, String commonName) {
    String userMessage =
        "Scientific name: "
            + scientificName
            + (commonName != null ? "\nCommon name: " + commonName : "");
    String prompt = DeepSeekClient.SPECIES_ENRICHMENT_SYSTEM_PROMPT + "\n\n" + userMessage;
    return DeepSeekClient.stripThinkTags(chat(prompt));
  }

  public String identifyPlant(byte[] imageBytes, String mediaType) {
    String base64 =
        Base64.getEncoder()
            .encodeToString(ImageUtil.resizeAndConvertToJpeg(imageBytes, OLLAMA_MAX_IMAGE_SIDE_PX));

    // llava-phi3 requires images at the TOP LEVEL of /api/generate — not nested in a chat message.
    String prompt =
        GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT
            + "\n\nIdentify this plant and generate a complete beginner care plan.";
    Map<String, Object> requestBody =
        Map.of("model", model, "prompt", prompt, "images", List.of(base64), "stream", false);

    log.debug("Sending vision prompt to Ollama [model={}] via /api/generate", model);
    try {
      OllamaGenerateResponse response =
          restClient
              .post()
              .uri("/api/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(OllamaGenerateResponse.class);

      if (response == null || response.response() == null || response.response().isBlank()) {
        throw new PlantPalException("Empty vision response from Ollama", 502);
      }
      String result = DeepSeekClient.stripThinkTags(response.response());
      log.debug("Ollama vision responded [model={}, response={}]", model, result);
      return result;

    } catch (RestClientException ex) {
      log.error("Ollama vision identification failed [model={}]", model, ex);
      throw new PlantPalException(
          "Ollama vision service unavailable — ensure Ollama is running locally", 503);
    }
  }

  public String analyzeRegions(byte[] imageBytes, String mediaType) {
    String base64 =
        Base64.getEncoder()
            .encodeToString(ImageUtil.resizeAndConvertToJpeg(imageBytes, OLLAMA_MAX_IMAGE_SIDE_PX));
    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "prompt",
            GitHubModelsClient.ANNOTATION_SYSTEM_PROMPT
                + "\n\nAnalyze this image and identify all plant and disease regions.",
            "images",
            List.of(base64),
            "stream",
            false);

    log.debug("Sending annotation prompt to Ollama [model={}] via /api/generate", model);
    try {
      OllamaGenerateResponse response =
          restClient
              .post()
              .uri("/api/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(OllamaGenerateResponse.class);

      if (response == null || response.response() == null || response.response().isBlank()) {
        throw new PlantPalException("Empty annotation response from Ollama", 502);
      }
      String result = DeepSeekClient.stripThinkTags(response.response());
      log.debug("Ollama annotation responded [model={}, response={}]", model, result);
      return result;

    } catch (RestClientException ex) {
      log.error("Ollama annotation failed [model={}]", model, ex);
      throw new PlantPalException(
          "Ollama annotation service unavailable — ensure Ollama is running locally", 503);
    }
  }

  // ── Private DTOs (Ollama wire format) ────────────────────────────────────

  private record OllamaChatRequest(String model, List<OllamaMessage> messages, boolean stream) {}

  private record OllamaMessage(String role, String content) {}

  private record OllamaChatResponse(OllamaMessage message, boolean done) {}

  private record OllamaGenerateResponse(String response, boolean done) {}
}
