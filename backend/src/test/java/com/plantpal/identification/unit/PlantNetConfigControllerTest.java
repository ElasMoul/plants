package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.gateway.GatewayProperties;
import com.plantpal.gateway.PlantNetGatewayClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.controller.PlantNetConfigController;
import com.plantpal.identification.dto.plantnet.PlantNetProjectDto;
import com.plantpal.identification.dto.plantnet.PlantNetQuotaDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Gap G4 follow-up: verifies {@link PlantNetConfigController} branches between the direct {@link
 * PlantNetClient} and {@link PlantNetGatewayClient} purely on {@code platform.gateway.enabled},
 * the same additive if/else shape used throughout the identification module for the gateway swap.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlantNetConfigController — gateway routing (gap G4 follow-up)")
class PlantNetConfigControllerTest {

  @Mock private PlantNetClient plantNetClient;
  @Mock private PlantNetGatewayClient plantNetGatewayClient;

  @Nested
  @DisplayName("gateway disabled")
  class GatewayDisabled {

    private PlantNetConfigController controller;

    @BeforeEach
    void setUp() {
      controller =
          new PlantNetConfigController(
              plantNetClient, plantNetGatewayClient, new GatewayProperties(false, "http://x"));
    }

    @Test
    void getProjects_usesDirectClient() {
      when(plantNetClient.getProjects(null, null, "en")).thenReturn(List.of());

      controller.getProjects(null, null, "en");

      verify(plantNetClient).getProjects(null, null, "en");
      verify(plantNetGatewayClient, never()).getProjects(any(), any(), any());
    }

    @Test
    void getLanguages_usesDirectClient() {
      when(plantNetClient.getLanguages()).thenReturn(List.of("en"));

      controller.getLanguages();

      verify(plantNetClient).getLanguages();
      verify(plantNetGatewayClient, never()).getLanguages();
    }

    @Test
    void getQuota_usesDirectClient() {
      when(plantNetClient.getQuota()).thenReturn(new PlantNetQuotaDto(10, 100));

      controller.getQuota();

      verify(plantNetClient).getQuota();
      verify(plantNetGatewayClient, never()).getQuota();
    }
  }

  @Nested
  @DisplayName("gateway enabled")
  class GatewayEnabled {

    private PlantNetConfigController controller;

    @BeforeEach
    void setUp() {
      controller =
          new PlantNetConfigController(
              plantNetClient, plantNetGatewayClient, new GatewayProperties(true, "http://x"));
    }

    @Test
    void getProjects_usesGatewayClient() {
      when(plantNetGatewayClient.getProjects(1.0, 2.0, "en"))
          .thenReturn(List.of(new PlantNetProjectDto("k-world-flora", "World flora", Map.of(), List.of("en"))));

      var response = controller.getProjects(1.0, 2.0, "en");

      assertThat(response.getBody().getData()).hasSize(1);
      verify(plantNetGatewayClient).getProjects(1.0, 2.0, "en");
      verify(plantNetClient, never()).getProjects(any(), any(), any());
    }

    @Test
    void getLanguages_usesGatewayClient() {
      when(plantNetGatewayClient.getLanguages()).thenReturn(List.of("en", "fr"));

      controller.getLanguages();

      verify(plantNetGatewayClient).getLanguages();
      verify(plantNetClient, never()).getLanguages();
    }

    @Test
    void getQuota_usesGatewayClient() {
      when(plantNetGatewayClient.getQuota()).thenReturn(new PlantNetQuotaDto(5, 50));

      var response = controller.getQuota();

      assertThat(response.getBody().getData().remaining()).isEqualTo(5);
      verify(plantNetGatewayClient).getQuota();
      verify(plantNetClient, never()).getQuota();
    }
  }
}
