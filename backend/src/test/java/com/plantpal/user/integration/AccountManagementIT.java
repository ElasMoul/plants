package com.plantpal.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DisplayName("Account management (PLA-24) - Integration Tests")
@TestPropertySource(properties = "app.session.enforcement-enabled=true")
class AccountManagementIT extends AbstractIntegrationTest {

  private static final String PREFS = "/api/v1/users/me/preferences";
  private static final String PASSWORD_URL = "/api/v1/users/me/password";
  private static final String EMAIL_URL = "/api/v1/users/me/email";
  private static final String VERIFY = "/api/v1/auth/verify-email";
  private static final String PASSWORD = "password123";
  private static final String NEW_PASSWORD = "brand-new-pass";

  @LocalServerPort private int port;

  private final TestRestTemplate rest =
      new TestRestTemplate(
          new RestTemplateBuilder().requestFactory(() -> new JdkClientHttpRequestFactory()));

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("profile names and language persist and come back in preferences")
  void profileUpdatePersists() {
    String email = uniqueEmail();
    String jwt = register(email);

    ResponseEntity<Map> updated =
        send(
            HttpMethod.PUT,
            PREFS,
            Map.of("firstName", " Ada ", "lastName", "Lovelace", "language", "fr"),
            jwt);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> prefs = data(send(HttpMethod.GET, PREFS, null, jwt));
    assertThat(prefs.get("firstName")).isEqualTo("Ada");
    assertThat(prefs.get("lastName")).isEqualTo("Lovelace");
    assertThat(prefs.get("language")).isEqualTo("fr");
    assertThat(prefs.get("email")).isEqualTo(email);
  }

  @Test
  @DisplayName("languages en, fr and ar are accepted; an unknown one is a 400")
  void languageValidation() {
    String jwt = register(uniqueEmail());

    for (String language : List.of("en", "fr", "ar")) {
      assertThat(send(HttpMethod.PUT, PREFS, Map.of("language", language), jwt).getStatusCode())
          .as(language)
          .isEqualTo(HttpStatus.OK);
    }
    assertThat(send(HttpMethod.PUT, PREFS, Map.of("language", "de"), jwt).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("blank and over-long names are a 400 and change nothing")
  void nameValidation() {
    String jwt = register(uniqueEmail());

    assertThat(send(HttpMethod.PUT, PREFS, Map.of("firstName", "   "), jwt).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            send(HttpMethod.PUT, PREFS, Map.of("lastName", "x".repeat(101)), jwt).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(data(send(HttpMethod.GET, PREFS, null, jwt)).get("firstName")).isEqualTo("Verif");
  }

  @Test
  @DisplayName("a profile update only ever touches the caller's own account")
  void otherUsersAreUntouched() {
    String otherEmail = uniqueEmail();
    register(otherEmail);
    String jwt = register(uniqueEmail());

    send(HttpMethod.PUT, PREFS, Map.of("firstName", "Mine"), jwt);

    assertThat(
            jdbc.queryForObject(
                "SELECT first_name FROM users WHERE email = ?", String.class, otherEmail))
        .isEqualTo("Verif");
  }

  @Test
  @DisplayName("a wrong or missing current password is refused and the hash is unchanged")
  void wrongCurrentPasswordRefused() {
    String email = uniqueEmail();
    String jwt = register(email);
    String before = hash(email);

    ResponseEntity<Map> wrong =
        send(
            HttpMethod.PUT,
            PASSWORD_URL,
            Map.of("currentPassword", "not-it-at-all", "newPassword", NEW_PASSWORD),
            jwt);
    ResponseEntity<Map> missing =
        send(HttpMethod.PUT, PASSWORD_URL, Map.of("newPassword", NEW_PASSWORD), jwt);

    assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(hash(email)).isEqualTo(before);
    assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("a weak new password is refused")
  void weakNewPasswordRefused() {
    String email = uniqueEmail();
    String jwt = register(email);

    ResponseEntity<Map> response =
        send(
            HttpMethod.PUT,
            PASSWORD_URL,
            Map.of("currentPassword", PASSWORD, "newPassword", "short"),
            jwt);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("a password change signs in with the new password, revokes other sessions only")
  void passwordChangeRevokesOtherSessions() {
    String email = uniqueEmail();
    String first = register(email);
    String second = tokenOf(login(email, PASSWORD));
    assertThat(send(HttpMethod.GET, PREFS, null, second).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> changed =
        send(
            HttpMethod.PUT,
            PASSWORD_URL,
            Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD),
            first);

    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(send(HttpMethod.GET, PREFS, null, first).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(send(HttpMethod.GET, PREFS, null, second).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("an email change mails the NEW address once, in the account language, and waits")
  void emailChangeStartsVerification() {
    String email = uniqueEmail();
    String jwt = register(email);
    send(HttpMethod.PUT, PREFS, Map.of("language", "fr"), jwt);
    String fresh = uniqueEmail();

    ResponseEntity<Map> response =
        send(
            HttpMethod.PUT, EMAIL_URL, Map.of("newEmail", fresh, "currentPassword", PASSWORD), jwt);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(outbox(fresh)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT language FROM email_outbox WHERE recipient = ?", String.class, fresh))
        .isEqualTo("fr");
    assertThat(outbox(email)).isEqualTo(1); // only the registration mail went to the old address
    assertThat(data(send(HttpMethod.GET, PREFS, null, jwt)))
        .containsEntry("email", email)
        .containsEntry("pendingEmail", fresh);
    assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login(fresh, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("the new address becomes the verified login only once its link is used")
  void emailChangeConfirmed() {
    String email = uniqueEmail();
    String jwt = register(email);
    String fresh = uniqueEmail();
    send(HttpMethod.PUT, EMAIL_URL, Map.of("newEmail", fresh, "currentPassword", PASSWORD), jwt);
    String token = plantToken(email, fresh, "chg_");

    ResponseEntity<Map> confirmed = post(VERIFY, Map.of("token", token));

    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            jdbc.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM users WHERE email = ?",
                Boolean.class,
                fresh))
        .isTrue();
    assertThat(login(fresh, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("a link without the change marker cannot be used to change the address")
  void registrationLinkIsNotAChangeLink() {
    String email = uniqueEmail();
    String jwt = register(email);
    String fresh = uniqueEmail();
    send(HttpMethod.PUT, EMAIL_URL, Map.of("newEmail", fresh, "currentPassword", PASSWORD), jwt);
    String plain = plantToken(email, fresh, "");

    assertThat(post(VERIFY, Map.of("token", plain)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("a taken, same or invalid address, or a wrong password, is refused with no email")
  void emailChangeRefusals() {
    String email = uniqueEmail();
    String taken = uniqueEmail();
    register(taken);
    String jwt = register(email);

    List<Map<String, Object>> bodies =
        List.of(
            Map.of("newEmail", taken, "currentPassword", PASSWORD),
            Map.of("newEmail", email, "currentPassword", PASSWORD),
            Map.of("newEmail", "not-an-email", "currentPassword", PASSWORD),
            Map.of("newEmail", uniqueEmail(), "currentPassword", "nope-nope-nope"));

    for (Map<String, Object> body : bodies) {
      assertThat(send(HttpMethod.PUT, EMAIL_URL, body, jwt).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(outbox(taken)).isEqualTo(1); // its own registration mail only
    assertThat(outbox(email)).isEqualTo(1);
  }

  // --- helpers -------------------------------------------------------------------------------

  private String register(String email) {
    ResponseEntity<Map> response =
        post(
            "/api/v1/auth/register",
            Map.of("email", email, "password", PASSWORD, "firstName", "Verif", "lastName", "Test"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return tokenOf(response);
  }

  private ResponseEntity<Map> login(String email, String password) {
    return post("/api/v1/auth/login", Map.of("email", email, "password", password));
  }

  private static String tokenOf(ResponseEntity<Map> response) {
    return (String) data(response).get("token");
  }

  /** Plants a link with a known value (the emailed one is not recoverable from the outbox). */
  private String plantToken(String email, String provenEmail, String prefix) {
    String token = prefix + UUID.randomUUID().toString().replace("-", "");
    jdbc.update(
        "UPDATE email_verification_tokens SET used_at = NOW() WHERE used_at IS NULL"
            + " AND user_id = (SELECT id FROM users WHERE email = ?)",
        email);
    jdbc.update(
        "INSERT INTO email_verification_tokens(user_id, token_hash, email, expires_at, created_at)"
            + " SELECT id, ?, ?, NOW() + INTERVAL '1 hour', NOW() - INTERVAL '5 minutes'"
            + " FROM users WHERE email = ?",
        sha256(token),
        provenEmail,
        email);
    return token;
  }

  private String hash(String email) {
    return jdbc.queryForObject(
        "SELECT password_hash FROM users WHERE email = ?", String.class, email);
  }

  private int outbox(String email) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM email_outbox WHERE recipient = ?"
            + " AND template = 'ACCOUNT_VERIFICATION'",
        Integer.class,
        email);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(ResponseEntity<Map> response) {
    return (Map<String, Object>) response.getBody().get("data");
  }

  private ResponseEntity<Map> post(String path, Object body) {
    return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body), Map.class);
  }

  private ResponseEntity<Map> send(HttpMethod method, String path, Object body, String jwt) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt);
    return rest.exchange(url(path), method, new HttpEntity<>(body, headers), Map.class);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private static String uniqueEmail() {
    return "account-" + UUID.randomUUID() + "@example.com";
  }
}
