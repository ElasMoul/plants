package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
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
      return response.message().content();

    } catch (RestClientException ex) {
      log.error("Failed to reach Ollama at configured base-url [model={}]", model, ex);
      throw new PlantPalException("AI service unavailable — ensure Ollama is running locally", 503);
    }
  }

  public String identifyPlant(byte[] imageBytes, String mediaType) {
    String base64 = Base64.getEncoder().encodeToString(imageBytes);

    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of(
                    "role", "system", "content", DeepSeekClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT),
                Map.of(
                    "role",
                    "user",
                    "content",
                    "Identify this plant and generate a complete beginner care plan.",
                    "images",
                    List.of(base64))),
            "stream",
            false);

    log.debug("Sending vision prompt to Ollama [model={}]", model);
    try {
      OllamaChatResponse response =
          restClient
              .post()
              .uri("/api/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(OllamaChatResponse.class);

      if (response == null || response.message() == null) {
        throw new PlantPalException("Empty vision response from Ollama", 502);
      }
      log.debug("Ollama vision responded [model={}]", model);
      return response.message().content();

    } catch (RestClientException ex) {
      log.error("Ollama vision identification failed [model={}]", model, ex);
      throw new PlantPalException(
          "Ollama vision service unavailable — ensure Ollama is running locally", 503);
    }
  }

  // ── Private DTOs (Ollama wire format) ────────────────────────────────────

  private record OllamaChatRequest(String model, List<OllamaMessage> messages, boolean stream) {}

  private record OllamaMessage(String role, String content) {}

  private record OllamaChatResponse(OllamaMessage message, boolean done) {}
}
