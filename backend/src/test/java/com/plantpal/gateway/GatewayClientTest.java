package com.plantpal.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.PlantPalException;
import com.sun.net.httpserver.HttpServer;
import io.platform.contracts.aigateway.AiRequest;
import io.platform.contracts.aigateway.AiResponse;
import io.platform.contracts.aigateway.BlockedResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link GatewayClient} over real HTTP against a loopback stub server (same technique as
 * pregnancy's AiGatewayClientTest / ai-gateway's own HttpPreflightPortTest) — verifies actual
 * request/response marshaling of the contracts types, not mocked logic.
 */
class GatewayClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void request_success_parsesResponse() throws IOException {
    AiResponse stubbed = new AiResponse();
    stubbed.setResult("Water every 7 days.");
    stubbed.setModel("claude-sonnet-4-6");
    stubbed.setProvider("anthropic");
    stubbed.setTokensIn(50);
    stubbed.setTokensOut(20);
    stubbed.setComputedCost(new BigDecimal("0.001"));
    GatewayClient client = clientReturning(200, stubbed);

    AiResponse response = client.request(sampleRequest());

    assertThat(response.getResult()).isEqualTo("Water every 7 days.");
    assertThat(response.getModel()).isEqualTo("claude-sonnet-4-6");
  }

  @Test
  void request_blocked402_throwsPlantPalExceptionWithReasonAndErrorCode402() throws IOException {
    BlockedResponse blocked = new BlockedResponse();
    blocked.setReason("global monthly ceiling reached");
    blocked.setCeiling(new BigDecimal("10.00"));
    blocked.setConsumed(new BigDecimal("10.01"));
    GatewayClient client = clientReturning(402, blocked);

    assertThatThrownBy(() -> client.request(sampleRequest()))
        .isInstanceOf(PlantPalException.class)
        .satisfies(
            e -> {
              PlantPalException ex = (PlantPalException) e;
              assertThat(ex.getMessage()).isEqualTo("global monthly ceiling reached");
              assertThat(ex.getErrorCode()).isEqualTo(402);
            });
  }

  @Test
  void request_serverError_failsClosedWith503() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/ai/request",
        exchange -> {
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });
    server.start();
    GatewayClient client = new GatewayClient(baseUrlFor(server), objectMapper);

    assertThatThrownBy(() -> client.request(sampleRequest()))
        .isInstanceOf(PlantPalException.class)
        .satisfies(e -> assertThat(((PlantPalException) e).getErrorCode()).isEqualTo(503));
  }

  @Test
  void request_unreachable_failsClosedWith503() throws IOException {
    int deadPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      deadPort = socket.getLocalPort();
    }
    // Socket closed on return above — nothing listening, connection refused.
    GatewayClient client =
        new GatewayClient(
            new GatewayProperties(true, "http://localhost:" + deadPort), objectMapper);

    assertThatThrownBy(() -> client.request(sampleRequest()))
        .isInstanceOf(PlantPalException.class)
        .satisfies(e -> assertThat(((PlantPalException) e).getErrorCode()).isEqualTo(503));
  }

  private GatewayClient clientReturning(int status, Object body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/ai/request",
        exchange -> {
          byte[] payload = objectMapper.writeValueAsBytes(body);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, payload.length);
          exchange.getResponseBody().write(payload);
          exchange.close();
        });
    server.start();
    return new GatewayClient(baseUrlFor(server), objectMapper);
  }

  private GatewayProperties baseUrlFor(HttpServer server) {
    return new GatewayProperties(true, "http://localhost:" + server.getAddress().getPort());
  }

  private AiRequest sampleRequest() {
    AiRequest request = new AiRequest();
    request.setPrompt("identify this plant");
    request.setAppId("plantpal");
    request.setUserId("1");
    return request;
  }
}
