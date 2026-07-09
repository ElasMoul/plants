package com.plantpal.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("SecretConfigValidator - Unit Tests")
class SecretConfigValidatorTest {

  // Same test-only values application-test.yml uses (SEC-4-style obvious fakes; the VAPID pair
  // is a freshly generated test-only pair, never used anywhere real).
  private static final String VALID_JWT_SECRET =
      "TESTONLYFAKEKEY0TESTONLYFAKEKEY0TESTONLYFAKEKEY0TESTONLYFAKEKEY0";
  private static final String VALID_VAPID_PUBLIC =
      "BLhowkMbZpHjPN2xWYz6BRLQKNSJ35tZGtSUW7YtXmuvPZ8Q3D1QGi1z9ajYVk5K9dQwrFAlZS2yny8XSB3XmNo";
  private static final String VALID_VAPID_PRIVATE = "adZn9Dosb0SoRcxR0G8VUzanNA9sLzFiGvH4jDVFqWA";

  // The literal placeholders from backend/.env.example that caused the two live incidents.
  private static final String PLACEHOLDER_JWT = "<generate: openssl rand -base64 64>";
  private static final String PLACEHOLDER_VAPID_PUBLIC = "your_vapid_public_key_here";
  private static final String PLACEHOLDER_VAPID_PRIVATE = "your_vapid_private_key_here";

  private final ConfigurableListableBeanFactory beanFactory =
      mock(ConfigurableListableBeanFactory.class);

  private SecretConfigValidator validatorWith(String jwt, String vapidPub, String vapidPriv) {
    MockEnvironment environment = new MockEnvironment();
    if (jwt != null) {
      environment.setProperty("app.jwt.secret", jwt);
    }
    if (vapidPub != null) {
      environment.setProperty("app.web-push.public-key", vapidPub);
    }
    if (vapidPriv != null) {
      environment.setProperty("app.web-push.private-key", vapidPriv);
    }
    // Mirror how Spring wires a BFPP: no-arg construct, then EnvironmentAware setter (constructor
    // injection is unavailable at BFPP-instantiation time — that was the PP-085 bug).
    SecretConfigValidator validator = new SecretConfigValidator();
    validator.setEnvironment(environment);
    return validator;
  }

  @Nested
  @DisplayName("valid configuration")
  class ValidConfiguration {

    @Test
    @DisplayName("should start silently when all three secrets are real values")
    void shouldPassWithValidValues() {
      SecretConfigValidator validator =
          validatorWith(VALID_JWT_SECRET, VALID_VAPID_PUBLIC, VALID_VAPID_PRIVATE);

      validator.postProcessBeanFactory(beanFactory); // must not throw
    }
  }

  @Nested
  @DisplayName("JWT_SECRET validation")
  class JwtSecretValidation {

    @Test
    @DisplayName("should name JWT_SECRET and the openssl fix when left as the SEC-4 placeholder")
    void shouldRejectPlaceholderJwtSecret() {
      SecretConfigValidator validator =
          validatorWith(PLACEHOLDER_JWT, VALID_VAPID_PUBLIC, VALID_VAPID_PRIVATE);

      assertThatThrownBy(() -> validator.postProcessBeanFactory(beanFactory))
          .isInstanceOf(ApplicationContextException.class)
          .hasMessageContaining("JWT_SECRET")
          .hasMessageContaining("not set to a real value")
          .hasMessageContaining("openssl rand -base64 64")
          .hasMessageNotContaining("VAPID");
    }

    @Test
    @DisplayName("should reject a JWT_SECRET that is not valid Base64")
    void shouldRejectNonBase64JwtSecret() {
      List<String> failures = SecretConfigValidator.validateJwtSecret("not!!valid@@base64%%");

      assertThat(failures).hasSize(1);
      assertThat(failures.get(0)).contains("JWT_SECRET").contains("not valid Base64");
    }

    @Test
    @DisplayName("should reject a valid-Base64 JWT_SECRET shorter than 32 decoded bytes")
    void shouldRejectShortJwtSecret() {
      String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

      List<String> failures = SecretConfigValidator.validateJwtSecret(sixteenBytes);

      assertThat(failures).hasSize(1);
      assertThat(failures.get(0)).contains("JWT_SECRET").contains("16 bytes").contains("32");
    }

    @Test
    @DisplayName("should name JWT_SECRET when the env var is not set at all")
    void shouldRejectMissingJwtSecret() {
      SecretConfigValidator validator =
          validatorWith(null, VALID_VAPID_PUBLIC, VALID_VAPID_PRIVATE);

      assertThatThrownBy(() -> validator.postProcessBeanFactory(beanFactory))
          .isInstanceOf(ApplicationContextException.class)
          .hasMessageContaining("JWT_SECRET is not set");
    }
  }

  @Nested
  @DisplayName("VAPID key validation")
  class VapidKeyValidation {

    @Test
    @DisplayName("should name VAPID_PUBLIC_KEY when it is not a 65-byte uncompressed P-256 point")
    void shouldRejectBadVapidPublicPoint() {
      // The .env.example placeholder is accidentally valid base64url — the point-shape
      // check is what catches it, mirroring the live "Invalid point encoding" incident.
      SecretConfigValidator validator =
          validatorWith(VALID_JWT_SECRET, PLACEHOLDER_VAPID_PUBLIC, VALID_VAPID_PRIVATE);

      assertThatThrownBy(() -> validator.postProcessBeanFactory(beanFactory))
          .isInstanceOf(ApplicationContextException.class)
          .hasMessageContaining("VAPID_PUBLIC_KEY")
          .hasMessageContaining("65 bytes")
          .hasMessageContaining("npx web-push generate-vapid-keys")
          .hasMessageNotContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("should reject a 65-byte public key that does not start with 0x04")
    void shouldRejectWrongPrefixVapidPublicKey() {
      byte[] point = new byte[65];
      point[0] = 0x05;
      String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(point);

      List<String> failures = SecretConfigValidator.validateVapidPublicKey(encoded);

      assertThat(failures).hasSize(1);
      assertThat(failures.get(0)).contains("VAPID_PUBLIC_KEY").contains("0x04");
    }

    @Test
    @DisplayName("should name VAPID_PRIVATE_KEY when it is not a 32-byte P-256 scalar")
    void shouldRejectPlaceholderVapidPrivateKey() {
      // "your_vapid_private_key_here" base64url-decodes fine — only the 32-byte length
      // check catches it.
      List<String> failures =
          SecretConfigValidator.validateVapidPrivateKey(PLACEHOLDER_VAPID_PRIVATE);

      assertThat(failures).hasSize(1);
      assertThat(failures.get(0))
          .contains("VAPID_PRIVATE_KEY")
          .contains("32 bytes")
          .contains("npx web-push generate-vapid-keys");
    }

    @Test
    @DisplayName("should reject a VAPID key that is not valid base64url")
    void shouldRejectNonBase64UrlVapidKey() {
      List<String> failures = SecretConfigValidator.validateVapidPublicKey("has+slash/and+plus==x");

      assertThat(failures).hasSize(1);
      assertThat(failures.get(0)).contains("VAPID_PUBLIC_KEY").contains("not valid base64url");
    }
  }

  @Nested
  @DisplayName("Spring BFPP lifecycle (PP-085 regression)")
  class SpringLifecycle {

    // Boots a real Spring context with ONLY the validator registered, so the BFPP is instantiated
    // through Spring's actual lifecycle. This is the path that was broken (constructor injection is
    // unavailable at invokeBeanFactoryPostProcessors time -> "No default constructor found"); the
    // mock-based tests above never exercised it. Guards against the EnvironmentAware wiring
    // regressing back to constructor injection.
    private AnnotationConfigApplicationContext contextWith(
        String jwt, String vapidPub, String vapidPriv) {
      Map<String, Object> props = new HashMap<>();
      props.put("app.jwt.secret", jwt);
      props.put("app.web-push.public-key", vapidPub);
      props.put("app.web-push.private-key", vapidPriv);
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
      context.register(SecretConfigValidator.class);
      return context;
    }

    @Test
    @DisplayName("should instantiate the BFPP and refresh cleanly with valid secrets")
    void shouldRefreshWithValidSecrets() {
      try (AnnotationConfigApplicationContext context =
          contextWith(VALID_JWT_SECRET, VALID_VAPID_PUBLIC, VALID_VAPID_PRIVATE)) {
        context.refresh(); // must not throw — the bug was a BeanCreationException here
        assertThat(context.getBean(SecretConfigValidator.class)).isNotNull();
      }
    }

    @Test
    @DisplayName(
        "should fail context refresh with the aggregated named-variable error on bad secrets")
    void shouldFailRefreshWithBadSecrets() {
      try (AnnotationConfigApplicationContext context =
          contextWith(PLACEHOLDER_JWT, VALID_VAPID_PUBLIC, VALID_VAPID_PRIVATE)) {
        assertThatThrownBy(context::refresh)
            .isInstanceOf(ApplicationContextException.class)
            .hasMessageContaining("JWT_SECRET");
      }
    }
  }

  @Nested
  @DisplayName("failure aggregation")
  class FailureAggregation {

    @Test
    @DisplayName("should report ALL invalid variables in one error, not just the first")
    void shouldAggregateAllFailures() {
      SecretConfigValidator validator =
          validatorWith(PLACEHOLDER_JWT, PLACEHOLDER_VAPID_PUBLIC, PLACEHOLDER_VAPID_PRIVATE);

      assertThatThrownBy(() -> validator.postProcessBeanFactory(beanFactory))
          .isInstanceOf(ApplicationContextException.class)
          .hasMessageContaining("3 problems")
          .hasMessageContaining("JWT_SECRET")
          .hasMessageContaining("VAPID_PUBLIC_KEY")
          .hasMessageContaining("VAPID_PRIVATE_KEY")
          .hasMessageContaining("backend/.env.example");
    }
  }
}
