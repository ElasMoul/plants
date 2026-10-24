package com.plantpal.reminder.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.repository.EmailOutboxRepository;
import com.plantpal.reminder.entity.ReminderPreferences;
import com.plantpal.reminder.repository.ReminderPreferencesRepository;
import com.plantpal.user.repository.UserRepository;
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

@DisplayName("Reminder preferences and email unsubscribe - integration")
class ReminderPreferencesIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ReminderPreferencesRepository preferencesRepository;
  @Autowired private EmailOutboxRepository outboxRepository;
  @Autowired private UserRepository userRepository;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;

  private String email;
  private String token;
  private Long userId;

  @BeforeEach
  void registerUser() {
    email = "reminder-prefs-" + UUID.randomUUID() + "@example.com";
    var request =
        Map.of("email", email, "password", "password123", "firstName", "Pref", "lastName", "Test");
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    token = (String) data.get("token");
    userId = userRepository.findByEmail(email).orElseThrow().getId();
  }

  private HttpEntity<Object> authed(Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }

  @Test
  @DisplayName("preferences endpoints require authentication")
  void preferencesRequireAuth() {
    ResponseEntity<Map> response =
        restTemplate.getForEntity(url("/api/v1/notifications/preferences"), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("GET returns defaults; PUT saves; invalid input is rejected")
  void getPutAndValidation() {
    ResponseEntity<Map> defaults =
        restTemplate.exchange(
            url("/api/v1/notifications/preferences"), HttpMethod.GET, authed(null), Map.class);
    assertThat(defaults.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(defaults)).containsEntry("channel", "PUSH");

    Map<String, Object> body =
        Map.of(
            "channel", "BOTH",
            "quietHoursStart", "22:00",
            "quietHoursEnd", "07:00",
            "timeZone", "Europe/Paris");
    ResponseEntity<Map> saved =
        restTemplate.exchange(
            url("/api/v1/notifications/preferences"), HttpMethod.PUT, authed(body), Map.class);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(saved))
        .containsEntry("channel", "BOTH")
        .containsEntry("quietHoursStart", "22:00");

    Map<String, Object> bad = Map.of("channel", "EMAIL", "timeZone", "Mars/Olympus");
    ResponseEntity<Map> rejected =
        restTemplate.exchange(
            url("/api/v1/notifications/preferences"), HttpMethod.PUT, authed(bad), Map.class);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName(
      "the unsubscribe link works without login, cancels pending digests, and re-opt-in clears it")
  void unsubscribeFlow() {
    restTemplate.exchange(
        url("/api/v1/notifications/preferences"),
        HttpMethod.PUT,
        authed(Map.of("channel", "EMAIL", "timeZone", "UTC")),
        Map.class);
    ReminderPreferences prefs = preferencesRepository.findById(userId).orElseThrow();
    EmailOutbox pending = new EmailOutbox();
    pending.setTemplate(EmailTemplate.REMINDER_DIGEST);
    pending.setRecipient(email);
    pending.setLanguage("en");
    pending.setVariables("{}");
    pending.setNextAttemptAt(java.time.Instant.now().plusSeconds(3600));
    Long pendingId = outboxRepository.save(pending).getId();

    ResponseEntity<String> page =
        restTemplate.getForEntity(
            url("/api/v1/email/unsubscribe?token=" + prefs.getUnsubscribeToken()), String.class);

    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(page.getBody()).contains("You are unsubscribed").contains("sabonn");
    assertThat(preferencesRepository.findById(userId).orElseThrow().isEmailUnsubscribed()).isTrue();
    EmailOutbox cancelled = outboxRepository.findById(pendingId).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(EmailStatus.FAILED);
    assertThat(cancelled.getLastError()).isEqualTo("unsubscribed");

    ResponseEntity<Map> reOptIn =
        restTemplate.exchange(
            url("/api/v1/notifications/preferences"),
            HttpMethod.PUT,
            authed(Map.of("channel", "BOTH", "timeZone", "UTC")),
            Map.class);
    assertThat(data(reOptIn)).containsEntry("emailUnsubscribed", false);
  }

  @Test
  @DisplayName("an unknown or missing token is a 404 and changes nothing")
  void unknownToken() {
    ResponseEntity<String> garbage =
        restTemplate.getForEntity(url("/api/v1/email/unsubscribe?token=garbage"), String.class);

    assertThat(garbage.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(ResponseEntity<Map> response) {
    return (Map<String, Object>) response.getBody().get("data");
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
