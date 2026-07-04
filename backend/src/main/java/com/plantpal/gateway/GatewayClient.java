package com.plantpal.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.PlantPalException;
import io.platform.contracts.aigateway.AiRequest;
import io.platform.contracts.aigateway.AiResponse;
import io.platform.contracts.aigateway.BlockedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The one path through which PlantPal talks to an LLM when {@code platform.gateway.enabled} is on
 * (D022 — no direct provider calls, no provider keys here). Modeled on pregnancy's
 * io.platform.pregnancy.gateway.AiGatewayClient — same RestClient-based pattern, same contracts
 * types — but reuses PlantPal's existing {@link PlantPalException} (errorCode 402/503) instead of
 * introducing new exception types, since GlobalExceptionHandler.handlePlantPal() already handles
 * any PlantPalException without further wiring.
 */
@Component
public class GatewayClient {

  private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public GatewayClient(GatewayProperties properties, ObjectMapper objectMapper) {
    this.restClient = RestClient.builder().baseUrl(properties.url()).build();
    this.objectMapper = objectMapper;
  }

  public AiResponse request(AiRequest request) {
    try {
      AiResponse response =
          restClient
              .post()
              .uri("/ai/request")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .onStatus(
                  status -> status.value() == 402,
                  (req, res) -> {
                    BlockedResponse blocked =
                        objectMapper.readValue(res.getBody(), BlockedResponse.class);
                    throw new PlantPalException(blocked.getReason(), 402);
                  })
              .onStatus(
                  HttpStatusCode::isError,
                  (req, res) -> {
                    throw new PlantPalException("ai-gateway unreachable", 503);
                  })
              .body(AiResponse.class);
      log.debug(
          "ai-gateway responded [modelHint={}, actualModel={}]",
          request.getModelHint(),
          response != null ? response.getModel() : null);
      return response;
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      throw new PlantPalException("ai-gateway unreachable", 503, e);
    }
  }
}
