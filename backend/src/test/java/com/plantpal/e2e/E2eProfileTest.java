package com.plantpal.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.gateway.GatewayClient;
import com.plantpal.gateway.GatewayProperties;
import io.platform.contracts.aigateway.AiRequest;
import io.platform.contracts.aigateway.AiRequestMediaInner;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("e2e profile - stubs and guards")
class E2eProfileTest {

  @Configuration
  static class Collaborators {
    @Bean
    GatewayProperties gatewayProperties() {
      return new GatewayProperties(true, "http://localhost:0");
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    GatewayClient realGatewayClient(GatewayProperties properties, ObjectMapper objectMapper) {
      return new GatewayClient(properties, objectMapper);
    }
  }

  private ApplicationContextRunner runner(String... profiles) {
    return new ApplicationContextRunner()
        .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles))
        .withUserConfiguration(Collaborators.class, StubGatewayClient.class, E2eProfileGuard.class);
  }

  @Nested
  @DisplayName("wiring")
  class Wiring {
    @Test
    @DisplayName("e2e profile makes the stub the primary gateway client")
    void e2eLoadsStub() {
      runner("e2e")
          .run(
              ctx -> {
                assertThat(ctx).hasSingleBean(StubGatewayClient.class);
                assertThat(ctx.getBean(GatewayClient.class)).isInstanceOf(StubGatewayClient.class);
              });
    }

    @Test
    @DisplayName("default profile does not load the stub or the guard")
    void defaultDoesNotLoadStub() {
      runner("dev")
          .run(
              ctx -> {
                assertThat(ctx).doesNotHaveBean(StubGatewayClient.class);
                assertThat(ctx).doesNotHaveBean(E2eProfileGuard.class);
                assertThat(ctx.getBean(GatewayClient.class))
                    .isNotInstanceOf(StubGatewayClient.class);
              });
    }

    @Test
    @DisplayName("e2e combined with prod fails the context")
    void e2eWithProdIsRefused() {
      runner("e2e", "prod").run(ctx -> assertThat(ctx).hasFailed());
    }
  }

  @Nested
  @DisplayName("E2eProfileGuard")
  class Guard {
    @Test
    @DisplayName("rejects staging and platform too")
    void rejectsDeployedProfiles() {
      for (String deployed : List.of("prod", "staging", "platform")) {
        var env = new MockEnvironment();
        env.setActiveProfiles("e2e", deployed);
        assertThatThrownBy(() -> new E2eProfileGuard(env).rejectDeployedProfiles())
            .isInstanceOf(IllegalStateException.class);
      }
    }
  }

  @Nested
  @DisplayName("StubGatewayClient answers")
  class Answers {
    private final StubGatewayClient stub =
        new StubGatewayClient(
            new GatewayProperties(true, "http://localhost:0"), new ObjectMapper());

    @Test
    @DisplayName("identification request returns the Monstera care plan")
    void identification() {
      AiRequest request =
          new AiRequest()
              .prompt("Identify")
              .putContextItem("systemPrompt", "You are an expert botanist and plant pathologist")
              .addMediaItem(new AiRequestMediaInner().data(new byte[] {1}).mimeType("image/jpeg"));
      assertThat(stub.request(request).getResult())
          .contains(StubGatewayClient.SPECIES)
          .contains("careCards");
    }

    @Test
    @DisplayName("annotation request returns regions")
    void annotation() {
      AiRequest request =
          new AiRequest()
              .prompt("Locate")
              .putContextItem("systemPrompt", "You are a computer vision system")
              .addMediaItem(new AiRequestMediaInner().data(new byte[] {1}).mimeType("image/jpeg"));
      assertThat(stub.request(request).getResult()).contains("regions");
    }

    @Test
    @DisplayName("chat request returns the canned reply")
    void chat() {
      AiRequest request =
          new AiRequest()
              .prompt("How often?")
              .putContextItem("systemPrompt", "You are PlantPal, a friendly assistant");
      assertThat(stub.request(request).getResult()).isEqualTo(StubGatewayClient.CHAT_REPLY);
    }

    @Test
    @DisplayName("unknown request still gets a deterministic answer")
    void fallback() {
      AiRequest request = new AiRequest().prompt("Cure?");
      assertThat(stub.request(request).getResult()).isEqualTo(StubGatewayClient.FALLBACK_TEXT);
    }
  }

  @Test
  @DisplayName("application-e2e.yml routes AI to the stub and keeps mail off the network")
  void settings() throws IOException {
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader().load("e2e", new ClassPathResource("application-e2e.yml"));
    PropertySource<?> props = sources.get(0);
    assertThat(String.valueOf(props.getProperty("platform.gateway.enabled"))).isEqualTo("true");
    assertThat(String.valueOf(props.getProperty("app.mail.transport"))).isEqualTo("log");
  }
}
