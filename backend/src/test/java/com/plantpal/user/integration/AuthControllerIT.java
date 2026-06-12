package com.plantpal.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("AuthController — Integration Tests")
class AuthControllerIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("POST /register should create user and return 201 with a JWT token")
  void shouldRegisterSuccessfully() {
    // Given
    var request =
        Map.of(
            "email", uniqueEmail(),
            "password", "password123",
            "firstName", "John",
            "lastName", "Doe");

    // When
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertThat(data).isNotNull();
    assertThat(data.get("token")).isNotNull().asString().isNotBlank();
    assertThat(data.get("email")).isNotNull();
  }

  @Test
  @DisplayName("POST /register with duplicate email should return 400")
  void shouldReturn400WhenEmailAlreadyRegistered() {
    // Given — register once successfully
    String email = uniqueEmail();
    var firstRequest =
        Map.of(
            "email", email,
            "password", "password123",
            "firstName", "John",
            "lastName", "Doe");
    restTemplate.postForEntity(url("/api/v1/auth/register"), firstRequest, Map.class);

    // When — try to register again with the same email
    var duplicateRequest =
        Map.of(
            "email", email,
            "password", "differentPass1",
            "firstName", "Jane",
            "lastName", "Doe");
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), duplicateRequest, Map.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("success")).isEqualTo(false);
  }

  @Test
  @DisplayName("POST /login with valid credentials should return 200 and a JWT token")
  void shouldLoginSuccessfully() {
    // Given — register the user first
    String email = uniqueEmail();
    String password = "password123";
    var registerRequest =
        Map.of("email", email, "password", password, "firstName", "John", "lastName", "Doe");
    restTemplate.postForEntity(url("/api/v1/auth/register"), registerRequest, Map.class);

    var loginRequest = Map.of("email", email, "password", password);

    // When
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/login"), loginRequest, Map.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertThat(data.get("token")).isNotNull().asString().isNotBlank();
    assertThat(data.get("email")).isEqualTo(email);
  }

  @Test
  @DisplayName("POST /login with wrong password should return 401")
  void shouldReturn401WhenPasswordIsWrong() {
    // Given
    String email = uniqueEmail();
    var registerRequest =
        Map.of(
            "email", email,
            "password", "correctPassword1",
            "firstName", "John",
            "lastName", "Doe");
    restTemplate.postForEntity(url("/api/v1/auth/register"), registerRequest, Map.class);

    var loginRequest = Map.of("email", email, "password", "wrongPassword");

    // When
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/login"), loginRequest, Map.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().get("success")).isEqualTo(false);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private String uniqueEmail() {
    return "it-" + UUID.randomUUID() + "@example.com";
  }
}
