package com.plantpal.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.dto.plantnet.PlantNetDiseaseResponse;
import com.plantpal.identification.dto.plantnet.PlantNetProjectDto;
import com.plantpal.identification.dto.plantnet.PlantNetQuotaDto;
import com.plantpal.shared.exception.PlantPalException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PlantNetGatewayClient} over real HTTP against a loopback stub server (same
 * technique as {@link GatewayClientTest}) — verifies actual request/response marshaling of
 * ai-gateway's {@code ApiResponse<String>} envelope, not mocked logic. Covers gap G4's follow-up
 * (PlantNet auxiliary lookups + disease cross-check routed through the gateway).
 */
class PlantNetGatewayClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void getProjects_parsesEnvelopeThenInnerJson() throws IOException {
    String innerJson =
        "[{\"id\":\"k-world-flora\",\"name\":\"World flora\",\"commonNames\":{},\"languages\":[\"en\"]}]";
    PlantNetGatewayClient client = clientReturning("/ai/plantnet/projects", 200, innerJson);

    List<PlantNetProjectDto> projects = client.getProjects(null, null, "en");

    assertThat(projects).hasSize(1);
    assertThat(projects.get(0).id()).isEqualTo("k-world-flora");
  }

  @Test
  void getLanguages_parsesEnvelopeThenInnerJson() throws IOException {
    PlantNetGatewayClient client =
        clientReturning("/ai/plantnet/languages", 200, "[\"en\",\"fr\"]");

    List<String> languages = client.getLanguages();

    assertThat(languages).containsExactly("en", "fr");
  }

  @Test
  void getQuota_parsesEnvelopeThenInnerJson() throws IOException {
    PlantNetGatewayClient client =
        clientReturning("/ai/plantnet/quota", 200, "{\"remaining\":123,\"total\":500}");

    PlantNetQuotaDto quota = client.getQuota();

    assertThat(quota.remaining()).isEqualTo(123);
    assertThat(quota.total()).isEqualTo(500);
  }

  @Test
  void getQuota_gatewayUnreachable_fallsBackToUnavailableSentinel() throws IOException {
    int deadPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      deadPort = socket.getLocalPort();
    }
    PlantNetGatewayClient client =
        new PlantNetGatewayClient(
            new GatewayProperties(true, "http://localhost:" + deadPort), objectMapper);

    PlantNetQuotaDto quota = client.getQuota();

    assertThat(quota.remaining()).isEqualTo(-1);
    assertThat(quota.total()).isEqualTo(-1);
  }

  @Test
  void getProjects_gatewayReturnsError_throwsPlantPalException502() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/ai/plantnet/projects",
        exchange -> {
          byte[] payload =
              objectMapper.writeValueAsBytes(Map.of("error", "PlantNet quota exhausted"));
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, payload.length);
          exchange.getResponseBody().write(payload);
          exchange.close();
        });
    server.start();
    PlantNetGatewayClient client = new PlantNetGatewayClient(baseUrlFor(server), objectMapper);

    assertThatThrownBy(() -> client.getProjects(null, null, "en"))
        .isInstanceOf(PlantPalException.class)
        .hasMessageContaining("PlantNet quota exhausted");
  }

  @Test
  void checkDisease_noCorroboration_returnsEmptyResponse() throws IOException {
    // ai-gateway surfaces a PlantNet 404 (no disease match) as ApiResponse.ok(null) — {"data":null}
    PlantNetGatewayClient client = clientReturningRaw("/ai/plantnet/disease-check", 200, "{}");

    PlantNetDiseaseResponse response =
        client.checkDisease(new byte[] {1, 2, 3}, "image/jpeg", List.of("leaf"), "en");

    assertThat(response.results()).isEmpty();
    assertThat(response.remainingIdentificationRequests()).isZero();
  }

  @Test
  void checkDisease_match_parsesEnvelopeThenInnerJson() throws IOException {
    String innerJson = "{\"results\":[],\"remainingIdentificationRequests\":42}";
    PlantNetGatewayClient client = clientReturning("/ai/plantnet/disease-check", 200, innerJson);

    PlantNetDiseaseResponse response =
        client.checkDisease(new byte[] {1, 2, 3}, "image/jpeg", List.of("leaf"), "en");

    assertThat(response.remainingIdentificationRequests()).isEqualTo(42);
  }

  @Test
  void checkDisease_gatewayUnreachable_fallsBackToEmptyResponse() throws IOException {
    int deadPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      deadPort = socket.getLocalPort();
    }
    PlantNetGatewayClient client =
        new PlantNetGatewayClient(
            new GatewayProperties(true, "http://localhost:" + deadPort), objectMapper);

    PlantNetDiseaseResponse response =
        client.checkDisease(new byte[] {1, 2, 3}, "image/jpeg", List.of("leaf"), "en");

    assertThat(response.results()).isEmpty();
    assertThat(response.remainingIdentificationRequests()).isZero();
  }

  /**
   * Wraps {@code innerJson} in ai-gateway's {@code ApiResponse<String>} envelope: {"data": "..."}
   */
  private PlantNetGatewayClient clientReturning(String path, int status, String innerJson)
      throws IOException {
    String envelope = "{\"data\":" + objectMapper.writeValueAsString(innerJson) + "}";
    return clientReturningRaw(path, status, envelope);
  }

  private PlantNetGatewayClient clientReturningRaw(String path, int status, String rawBody)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        path,
        exchange -> {
          byte[] payload = rawBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, payload.length);
          exchange.getResponseBody().write(payload);
          exchange.close();
        });
    server.start();
    return new PlantNetGatewayClient(baseUrlFor(server), objectMapper);
  }

  private GatewayProperties baseUrlFor(HttpServer server) {
    return new GatewayProperties(true, "http://localhost:" + server.getAddress().getPort());
  }
}
