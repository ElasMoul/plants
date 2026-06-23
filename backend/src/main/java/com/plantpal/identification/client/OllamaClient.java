package com.plantpal.identification.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.util.ImageUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OllamaClient {

  private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

  // Conservative cap shared by every local vision model we've run (llava-phi3, gemma3); keeps
  // request size + latency predictable regardless of which model is currently pulled.
  private static final int OLLAMA_MAX_IMAGE_SIDE_PX = 1024;

  private final RestClient restClient;
  private final String model;
  private final String keepAlive;
  private final ObjectMapper objectMapper;

  public OllamaClient(
      @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
      @Value("${ollama.model:gemma3:4b}") String model,
      @Value("${ollama.read-timeout-minutes:5}") int readTimeoutMinutes,
      @Value("${ollama.connect-timeout-seconds:10}") int connectTimeoutSeconds,
      @Value("${ollama.keep-alive:10m}") String keepAlive,
      ObjectMapper objectMapper) {
    this.model = model;
    this.keepAlive = keepAlive;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutSeconds * 1_000);
    factory.setReadTimeout(readTimeoutMinutes * 60 * 1_000);
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.objectMapper = objectMapper;
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
        new OllamaChatRequest(
            model, List.of(new OllamaMessage("user", userPrompt)), false, keepAlive);

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
   * Streams a single-turn prompt to Ollama, invoking {@code onToken} once per content chunk as it
   * arrives (Ollama's NDJSON streaming format — one JSON object per line, {@code done:true} on the
   * final line). Runs synchronously on the calling thread — the caller (ChatServiceImpl /
   * ChatController) is responsible for running this off the HTTP request thread.
   *
   * @throws PlantPalException if Ollama is unreachable or the stream can't be read
   */
  public void chatStream(String userPrompt, Consumer<String> onToken) {
    log.debug("Streaming prompt to Ollama [model={}]: {}", model, userPrompt);

    OllamaChatRequest request =
        new OllamaChatRequest(
            model, List.of(new OllamaMessage("user", userPrompt)), true, keepAlive);

    try {
      restClient
          .post()
          .uri("/api/chat")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .exchange(
              (req, resp) -> {
                readStreamedTokens(resp.getBody(), onToken);
                return null;
              });
      log.debug("Ollama stream completed [model={}]", model);
    } catch (RestClientException ex) {
      log.error("Failed to stream from Ollama at configured base-url [model={}]", model, ex);
      throw new PlantPalException("AI service unavailable — ensure Ollama is running locally", 503);
    }
  }

  private void readStreamedTokens(InputStream body, Consumer<String> onToken) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        OllamaChatResponse chunk = objectMapper.readValue(line, OllamaChatResponse.class);
        if (chunk.message() != null && chunk.message().content() != null) {
          onToken.accept(chunk.message().content());
        }
      }
    } catch (IOException e) {
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

  /**
   * Local-model equivalent of {@link DeepSeekClient#generateCureAdvice}, used when the triggering
   * user's ReasoningModelPreference is OLLAMA_LLAVA. Same reuse-the-prompt-constant approach as
   * {@link #generateSpeciesEnrichment}.
   */
  public String generateCureAdvice(String species, String regionLabel) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage =
        "My "
            + effectiveSpecies
            + " has the following issue: "
            + regionLabel
            + ". Provide a concise cure procedure in 3-5 numbered steps.";
    String prompt = DeepSeekClient.CURE_ADVICE_SYSTEM_PROMPT + "\n\n" + userMessage;
    return DeepSeekClient.stripThinkTags(chat(prompt));
  }

  /**
   * Local-model equivalent of {@link DeepSeekClient#generateDiseaseDescription}, used when the
   * triggering user's ReasoningModelPreference is OLLAMA_LLAVA.
   */
  public String generateDiseaseDescription(String species, String diseaseName) {
    String effectiveSpecies = species != null ? species : "Unknown plant";
    String userMessage = "Plant: " + effectiveSpecies + "\nDisease/pest issue: " + diseaseName;
    String prompt = DeepSeekClient.DISEASE_DESCRIPTION_SYSTEM_PROMPT + "\n\n" + userMessage;
    return DeepSeekClient.stripThinkTags(chat(prompt));
  }

  public String identifyPlant(byte[] imageBytes, String mediaType) {
    String base64 =
        Base64.getEncoder()
            .encodeToString(ImageUtil.resizeAndConvertToJpeg(imageBytes, OLLAMA_MAX_IMAGE_SIDE_PX));

    // /api/generate's top-level "images" array works for any multimodal-capable model (llava-phi3,
    // gemma3, ...) — not nested in a chat message.
    String prompt =
        GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT
            + "\n\nIdentify this plant and generate a complete beginner care plan.";
    Map<String, Object> requestBody =
        Map.of(
            "model", model,
            "prompt", prompt,
            "images", List.of(base64),
            "stream", false,
            "keep_alive", keepAlive);

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
            false,
            "keep_alive",
            keepAlive);

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

  private record OllamaChatRequest(
      String model,
      List<OllamaMessage> messages,
      boolean stream,
      @JsonProperty("keep_alive") String keepAlive) {}

  private record OllamaMessage(String role, String content) {}

  private record OllamaChatResponse(OllamaMessage message, boolean done) {}

  private record OllamaGenerateResponse(String response, boolean done) {}
}
