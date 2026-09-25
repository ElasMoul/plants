package com.plantpal.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Guards the assumption that makes {@code csrf().disable()} in SecurityConfig safe (Sonar
 * java:S4502): credentials travel only in the {@code Authorization: Bearer} header, which a browser
 * never attaches to a cross-site request on its own. If cookie-based auth or a session cookie is
 * ever introduced, these tests fail — and CSRF protection must be re-enabled for that change.
 */
@DisplayName("CSRF exposure guard — auth must never ride on cookies")
class CsrfExposureIT extends AbstractIntegrationTest {

  private static final List<String> COOKIE_NAMES =
      List.of("token", "jwt", "access_token", "Authorization", "JSESSIONID", "SESSION");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;

  private String email;
  private String token;
  private ResponseEntity<Map> registerResponse;

  @BeforeEach
  void setUp() {
    email = "csrf-" + UUID.randomUUID() + "@example.com";
    var request =
        Map.of("email", email, "password", "password123", "firstName", "Csrf", "lastName", "Guard");
    registerResponse = restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) registerResponse.getBody().get("data");
    token = (String) data.get("token");
  }

  @Test
  @DisplayName("control: the token authenticates when sent as a Bearer header")
  void bearerHeaderAuthenticates() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    ResponseEntity<Map> response =
        restTemplate.exchange(
            url("/api/v1/plants"), HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("the same valid token in a cookie is ignored for reads and writes")
  void tokenInCookieIsRejected() {
    for (String name : COOKIE_NAMES) {
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.COOKIE, name + "=" + token);
      headers.set("Content-Type", "application/json");

      ResponseEntity<Map> read =
          restTemplate.exchange(
              url("/api/v1/plants"), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
      ResponseEntity<Map> write =
          restTemplate.exchange(
              url("/api/v1/plants"),
              HttpMethod.POST,
              new HttpEntity<>(Map.of("nickname", "Forged"), headers),
              Map.class);

      assertThat(read.getStatusCode())
          .as("GET with cookie %s", name)
          .isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(write.getStatusCode())
          .as("POST with cookie %s", name)
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Test
  @DisplayName("register and login set no cookies a browser could replay cross-site")
  void authResponsesSetNoCookies() {
    ResponseEntity<Map> login =
        restTemplate.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("email", email, "password", "password123"),
            Map.class);

    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(registerResponse.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
    assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
