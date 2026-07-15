package com.plantpal.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.plantpal.identification.dto.plantnet.PlantNetDiseaseResponse;
import com.plantpal.identification.dto.plantnet.PlantNetProjectDto;
import com.plantpal.identification.dto.plantnet.PlantNetQuotaDto;
import com.plantpal.shared.exception.PlantPalException;
import io.platform.contracts.aigateway.AiRequestMediaInner;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Gateway routing for PlantNet auxiliary lookups (gap G4 follow-up to the ai-gateway full-coverage
 * demand) — projects/languages/quota metadata and the disease cross-check. {@code identify()}
 * itself already routes through {@link GatewayClient}'s {@code /ai/request} (modelHint {@code
 * "plantnet"}, see {@code IdentificationServiceImpl.runIdentification}); these four calls have no
 * {@code ai.request} shape of their own (no prompt/modelHint — metadata lookups and a second image
 * upload, not generative calls), so ai-gateway exposes them as their own thin endpoints instead
 * (see ai-gateway's {@code PlantNetController} javadoc), and this client talks to those directly.
 *
 * <p>Every response is ai-gateway's own {@code ApiResponse<String>} envelope ({@code {data,
 * error}}, house convention for a gateway-invented response shape) wrapping raw PlantNet JSON as a
 * string — this class unwraps the envelope, then parses the inner string onto PlantPal's existing
 * PlantNet DTOs, the same DTOs the direct {@link com.plantpal.identification.client.PlantNetClient}
 * / {@link com.plantpal.identification.client.PlantNetDiseaseClient} path already parses onto, so
 * callers see an identical return type regardless of which path served the call.
 */
@Component
public class PlantNetGatewayClient {

  private static final Logger log = LoggerFactory.getLogger(PlantNetGatewayClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public PlantNetGatewayClient(GatewayProperties properties, ObjectMapper objectMapper) {
    this.restClient = RestClient.builder().baseUrl(properties.url()).build();
    this.objectMapper = objectMapper;
  }

  /** GET /ai/plantnet/projects — mirrors {@code PlantNetClient#getProjects}. */
  public List<PlantNetProjectDto> getProjects(Double lat, Double lon, String lang) {
    String raw =
        fetch(
            uriBuilder -> {
              UriBuilder b = uriBuilder.path("/ai/plantnet/projects");
              if (lat != null) {
                b = b.queryParam("lat", lat);
              }
              if (lon != null) {
                b = b.queryParam("lon", lon);
              }
              if (lang != null && !lang.isBlank()) {
                b = b.queryParam("lang", lang);
              }
              return b.build();
            },
            "PlantNet project list");
    return parseList(raw, PlantNetProjectDto.class, "project list");
  }

  /** GET /ai/plantnet/languages — mirrors {@code PlantNetClient#getLanguages}. */
  public List<String> getLanguages() {
    String raw =
        fetch(uriBuilder -> uriBuilder.path("/ai/plantnet/languages").build(), "language list");
    return parseList(raw, String.class, "language list");
  }

  /**
   * GET /ai/plantnet/quota — mirrors {@code PlantNetClient#getQuota}, including its fallback: a
   * -1/-1 quota when the gateway or PlantNet is unreachable, so the UI can show "unavailable"
   * rather than breaking.
   */
  public PlantNetQuotaDto getQuota() {
    try {
      String raw = fetch(uriBuilder -> uriBuilder.path("/ai/plantnet/quota").build(), "quota");
      if (raw == null) {
        return new PlantNetQuotaDto(-1, -1);
      }
      return objectMapper.readValue(raw, PlantNetQuotaDto.class);
    } catch (PlantPalException | JsonProcessingException e) {
      log.warn("Failed to fetch PlantNet quota via gateway: {}", e.getMessage());
      return new PlantNetQuotaDto(-1, -1);
    }
  }

  /**
   * POST /ai/plantnet/disease-check — mirrors {@code PlantNetDiseaseClient#identifyDisease}
   * semantics exactly: a null/absent result (PlantNet 404, no corroboration) and an unreachable
   * gateway both fall back to an empty response rather than propagating an error, since this is a
   * best-effort second opinion running alongside annotation, not the primary identification path.
   */
  public PlantNetDiseaseResponse checkDisease(
      byte[] imageBytes, String mediaType, List<String> organs, String lang) {
    GatewayDiseaseCheckRequest body =
        new GatewayDiseaseCheckRequest(
            List.of(new AiRequestMediaInner().data(imageBytes).mimeType(mediaType)),
            organs != null && !organs.isEmpty() ? organs : List.of("auto"),
            lang);
    try {
      GatewayEnvelope envelope =
          restClient
              .post()
              .uri("/ai/plantnet/disease-check")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(GatewayEnvelope.class);
      String raw = envelope != null ? envelope.data() : null;
      if (raw == null) {
        return new PlantNetDiseaseResponse(List.of(), 0);
      }
      return objectMapper.readValue(raw, PlantNetDiseaseResponse.class);
    } catch (RestClientException | JsonProcessingException e) {
      log.warn(
          "PlantNet disease cross-check via gateway unreachable, continuing without it: {}",
          e.getMessage());
      return new PlantNetDiseaseResponse(List.of(), 0);
    }
  }

  private String fetch(Function<UriBuilder, URI> uriFn, String description) {
    try {
      GatewayEnvelope envelope = restClient.get().uri(uriFn).retrieve().body(GatewayEnvelope.class);
      if (envelope == null) {
        throw new PlantPalException("Empty response fetching PlantNet " + description, 502);
      }
      if (envelope.error() != null) {
        throw new PlantPalException(envelope.error(), 502);
      }
      return envelope.data();
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      throw new PlantPalException(
          "ai-gateway unreachable fetching PlantNet " + description, 503, e);
    }
  }

  private <T> List<T> parseList(String raw, Class<T> elementType, String description) {
    if (raw == null) {
      return List.of();
    }
    try {
      CollectionType type =
          objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
      return objectMapper.readValue(raw, type);
    } catch (JsonProcessingException e) {
      throw new PlantPalException("Failed to parse PlantNet gateway " + description, 502, e);
    }
  }

  /** Ai-gateway's own {@code ApiResponse<String>} envelope shape — {@code {data, error}}. */
  private record GatewayEnvelope(String data, String error) {}

  /** Mirrors ai-gateway's {@code PlantNetDiseaseCheckRequest} — reuses the contracts media type. */
  private record GatewayDiseaseCheckRequest(
      List<AiRequestMediaInner> media, List<String> organs, String lang) {}
}
