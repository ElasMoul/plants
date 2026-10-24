package com.plantpal.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.gateway.GatewayClient;
import com.plantpal.gateway.GatewayProperties;
import io.platform.contracts.aigateway.AiRequest;
import io.platform.contracts.aigateway.AiResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for the AI gateway, active only under the {@code e2e} profile. With {@code
 * platform.gateway.enabled=true} every vision, annotation, reasoning and chat call is routed
 * through {@link GatewayClient#request}, so overriding that one method means no real provider is
 * ever contacted. Answers depend only on the request's system prompt and media, never on time or
 * randomness.
 */
@Component
@Profile("e2e")
@Primary
public class StubGatewayClient extends GatewayClient {

  /** Species the stubbed identification returns; the smoke test asserts on it. */
  public static final String SPECIES = "Monstera deliciosa";

  /** Common name the stubbed identification returns. */
  public static final String COMMON_NAME = "Swiss Cheese Plant";

  /** Reply the stubbed chat assistant returns; the smoke test asserts on it. */
  public static final String CHAT_REPLY =
      "Water your Swiss Cheese Plant when the top two inches of soil feel dry.";

  static final String IDENTIFICATION_JSON =
      """
      {
        "species": "Monstera deliciosa",
        "commonName": "Swiss Cheese Plant",
        "confidence": "HIGH",
        "healthStatus": "HEALTHY",
        "healthNotes": null,
        "carePlan": {
          "wateringFrequencyDays": 7,
          "fertilizingFrequencyDays": 30,
          "repottingFrequencyMonths": 18,
          "careCards": [
            {
              "type": "WATERING",
              "title": "Water weekly",
              "icon": "water_drop",
              "summary": "Water when the top soil is dry.",
              "detail": "Check the top two inches of soil. Water thoroughly when dry.",
              "urgency": "MEDIUM",
              "seasonalVariation": null,
              "actionPlan": null
            },
            {
              "type": "LIGHT",
              "title": "Bright indirect light",
              "icon": "wb_sunny",
              "summary": "Keep out of direct midday sun.",
              "detail": "A spot near an east-facing window works well.",
              "urgency": "LOW",
              "seasonalVariation": null,
              "actionPlan": null
            }
          ],
          "beginnerWarnings": ["Toxic to pets if chewed"]
        }
      }
      """;

  static final String ANNOTATION_JSON =
      """
      {"regions": [{"label": "Monstera leaf", "type": "PLANT", "confidence": "HIGH",
        "polygon": [{"xPct": 10, "yPct": 10}, {"xPct": 90, "yPct": 10},
                    {"xPct": 90, "yPct": 90}, {"xPct": 10, "yPct": 90}]}]}
      """;

  static final String FALLBACK_TEXT =
      "1. Check the plant.\n2. Water lightly.\n3. Review again next week.";

  public StubGatewayClient(GatewayProperties properties, ObjectMapper objectMapper) {
    super(properties, objectMapper);
  }

  @Override
  public AiResponse request(AiRequest request) {
    return response(answerFor(request));
  }

  private static String answerFor(AiRequest request) {
    Object prompt = request.getContext() == null ? null : request.getContext().get("systemPrompt");
    String systemPrompt = prompt == null ? "" : prompt.toString();
    List<?> media = request.getMedia();
    if (media != null && !media.isEmpty()) {
      return systemPrompt.contains("computer vision") ? ANNOTATION_JSON : IDENTIFICATION_JSON;
    }
    if (systemPrompt.contains("You are PlantPal")) {
      return CHAT_REPLY;
    }
    if (systemPrompt.contains("expert botanist")) {
      return "{}";
    }
    return FALLBACK_TEXT;
  }

  private static AiResponse response(String result) {
    AiResponse response = new AiResponse();
    response.setResult(result);
    response.setModel("e2e-stub");
    response.setProvider("e2e-stub");
    response.setTokensIn(1);
    response.setTokensOut(1);
    response.setComputedCost(BigDecimal.ZERO);
    return response;
  }
}
