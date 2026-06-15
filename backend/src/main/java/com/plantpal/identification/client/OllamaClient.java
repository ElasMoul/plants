package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
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

  public String identifyPlant(byte[] imageBytes, String mediaType) {
    String base64 = Base64.getEncoder().encodeToString(resizeAndConvertToJpeg(imageBytes));

    // llava-phi3 requires images at the TOP LEVEL of /api/generate — not nested in a chat message.
    String prompt =
        DeepSeekClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT
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
    String base64 = Base64.getEncoder().encodeToString(resizeAndConvertToJpeg(imageBytes));
    Map<String, Object> requestBody =
        Map.of(
            "model",
            model,
            "prompt",
            DeepSeekClient.ANNOTATION_SYSTEM_PROMPT
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

  // ── Private helpers ──────────────────────────────────────────────────────

  private byte[] resizeAndConvertToJpeg(byte[] original) {
    try {
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
      if (img == null) {
        log.debug("ImageIO could not decode image — sending original bytes to Ollama");
        return original;
      }
      int w = img.getWidth(), h = img.getHeight();
      double scale = Math.min(1.0, (double) OLLAMA_MAX_IMAGE_SIDE_PX / Math.max(w, h));
      int newW = (int) (w * scale), newH = (int) (h * scale);
      BufferedImage output = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = output.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(img, 0, 0, newW, newH, null);
      g.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(output, "JPEG", out);
      byte[] result = out.toByteArray();
      log.debug(
          "Resized image for Ollama: {}x{} → {}x{} ({} KB → {} KB)",
          w,
          h,
          newW,
          newH,
          original.length / 1024,
          result.length / 1024);
      return result;
    } catch (Exception e) {
      log.debug("Image resize for Ollama failed ({}), using original bytes", e.getMessage());
      return original;
    }
  }

  // ── Private DTOs (Ollama wire format) ────────────────────────────────────

  private record OllamaChatRequest(String model, List<OllamaMessage> messages, boolean stream) {}

  private record OllamaMessage(String role, String content) {}

  private record OllamaChatResponse(OllamaMessage message, boolean done) {}

  private record OllamaGenerateResponse(String response, boolean done) {}
}
