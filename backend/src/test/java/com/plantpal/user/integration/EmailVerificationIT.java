package com.plantpal.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@DisplayName("Email verification — Integration Tests")
class EmailVerificationIT extends AbstractIntegrationTest {

  private static final String VERIFY = "/api/v1/auth/verify-email";
  private static final String RESEND = "/api/v1/auth/verify-email/resend";
  private static final String PASSWORD = "password123";

  @LocalServerPort private int port;

  // The default Apache client silently retries a 429 after its Retry-After wait (60 s here), which
  // would turn the very response these tests assert into a later 200; use a plain JDK client.
  private final TestRestTemplate rest =
      new TestRestTemplate(
          new RestTemplateBuilder().requestFactory(() -> new JdkClientHttpRequestFactory()));

  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("registering queues one verification email and one unspent token")
  void registrationIssuesVerification() {
    String email = uniqueEmail();
    register(email);

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens t JOIN users u ON u.id=t.user_id "
                    + "WHERE u.email=? AND t.used_at IS NULL",
                Integer.class,
                email))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_outbox WHERE recipient=? AND template='ACCOUNT_VERIFICATION'",
                Integer.class,
                email))
        .isEqualTo(1);
    assertThat(isVerified(email)).isFalse();
  }

  @Test
  @DisplayName("a link works once; reusing it gives the generic 400")
  void linkWorksOnce() {
    String email = uniqueEmail();
    register(email);
    String token = plantToken(email, Instant.now().plusSeconds(3600));

    ResponseEntity<Map> first = post(VERIFY, Map.of("token", token), null);
    ResponseEntity<Map> second = post(VERIFY, Map.of("token", token), null);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(isVerified(email)).isTrue();
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("an expired link is refused and the user stays unverified")
  void expiredLinkRefused() {
    String email = uniqueEmail();
    register(email);
    String token = plantToken(email, Instant.now().minusSeconds(1));

    ResponseEntity<Map> response = post(VERIFY, Map.of("token", token), null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(isVerified(email)).isFalse();
  }

  @Test
  @DisplayName("an altered or random token gets the same refusal as a reused one")
  void alteredTokenRefused() {
    String email = uniqueEmail();
    register(email);
    String token = plantToken(email, Instant.now().plusSeconds(3600));
    String altered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

    ResponseEntity<Map> tampered = post(VERIFY, Map.of("token", altered), null);
    ResponseEntity<Map> random = post(VERIFY, Map.of("token", UUID.randomUUID().toString()), null);
    post(VERIFY, Map.of("token", token), null);
    ResponseEntity<Map> reused = post(VERIFY, Map.of("token", token), null);

    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(random.getBody().get("message")).isEqualTo(tampered.getBody().get("message"));
    assertThat(reused.getBody().get("message")).isEqualTo(tampered.getBody().get("message"));
    assertThat(reused.getBody().get("errorCode")).isEqualTo(tampered.getBody().get("errorCode"));
  }

  @Test
  @DisplayName("a link issued for a previous address no longer verifies")
  void changedEmailRefused() {
    String email = uniqueEmail();
    register(email);
    String token = plantToken(email, Instant.now().plusSeconds(3600));
    String moved = uniqueEmail();
    jdbc.update("UPDATE users SET email = ? WHERE email = ?", moved, email);

    ResponseEntity<Map> response = post(VERIFY, Map.of("token", token), null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(isVerified(moved)).isFalse();
  }

  @Test
  @DisplayName("concurrent use of one link succeeds exactly once")
  void concurrentConsumeSucceedsOnce() throws Exception {
    String email = uniqueEmail();
    register(email);
    String token = plantToken(email, Instant.now().plusSeconds(3600));
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<HttpStatus>> calls = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        calls.add(
            () ->
                HttpStatus.valueOf(
                    post(VERIFY, Map.of("token", token), null).getStatusCode().value()));
      }
      long ok = 0;
      for (Future<HttpStatus> f : pool.invokeAll(calls)) {
        if (f.get() == HttpStatus.OK) ok++;
      }
      assertThat(ok).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(isVerified(email)).isTrue();
  }

  @Test
  @DisplayName("resend: a second request inside 60 seconds is 429, then the hourly cap applies")
  void resendRateLimited() {
    String email = uniqueEmail();
    String jwt = register(email);

    // Registration already issued the first link, so an immediate resend hits the cooldown.
    ResponseEntity<Map> tooSoon = post(RESEND, null, jwt);
    assertThat(tooSoon.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(tooSoon.getHeaders().getFirst("Retry-After")).isNotNull();

    // Five links an hour ago-ish: past the cooldown but at the hourly cap.
    jdbc.update(
        "UPDATE email_verification_tokens SET created_at = NOW() - INTERVAL '2 minutes' "
            + "WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        email);
    for (int i = 0; i < 4; i++) {
      jdbc.update(
          "INSERT INTO email_verification_tokens(user_id, token_hash, email, expires_at, used_at, created_at) "
              + "SELECT id, ?, email, NOW() + INTERVAL '1 day', NOW(), NOW() - INTERVAL '10 minutes' "
              + "FROM users WHERE email = ?",
          sha256(UUID.randomUUID().toString()),
          email);
    }
    assertThat(post(RESEND, null, jwt).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  @DisplayName("resend after the cooldown sends a new link and retires the earlier one")
  void resendRetiresEarlierLink() {
    String email = uniqueEmail();
    String jwt = register(email);
    String oldToken = plantToken(email, Instant.now().plusSeconds(3600));
    jdbc.update(
        "UPDATE email_verification_tokens SET created_at = NOW() - INTERVAL '2 minutes' "
            + "WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        email);

    ResponseEntity<Map> resent = post(RESEND, null, jwt);

    assertThat(resent.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(post(VERIFY, Map.of("token", oldToken), null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("resend for an already verified user is a 200 no-op with no email queued")
  void resendNoOpWhenVerified() {
    String email = uniqueEmail();
    String jwt = register(email);
    jdbc.update("UPDATE users SET email_verified_at = NOW() WHERE email = ?", email);
    int before = outboxCount(email);

    ResponseEntity<Map> resent = post(RESEND, null, jwt);

    assertThat(resent.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(outboxCount(email)).isEqualTo(before);
  }

  @Test
  @DisplayName("resend needs a signed-in user")
  void resendRequiresAuth() {
    assertThat(post(RESEND, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("a pre-existing user is unverified, can still log in, and sees emailVerified=false")
  void existingUserNotVerifiedNotLockedOut() {
    String email = uniqueEmail();
    users.saveAndFlush(
        User.builder()
            .firstName("Legacy")
            .lastName("User")
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .build());

    ResponseEntity<Map> login =
        post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null);
    String jwt = (String) data(login).get("token");

    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(isVerified(email)).isFalse();
    ResponseEntity<Map> prefs = get("/api/v1/users/me/preferences", jwt);
    assertThat(prefs.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(prefs).get("emailVerified")).isEqualTo(false);
    assertThat(get("/api/v1/plants", jwt).getStatusCode()).isEqualTo(HttpStatus.OK);

    jdbc.update("UPDATE users SET email_verified_at = NOW() WHERE email = ?", email);
    assertThat(data(get("/api/v1/users/me/preferences", jwt)).get("emailVerified")).isEqualTo(true);
  }

  @Test
  @DisplayName("an unverified user gets 403 EMAIL_NOT_VERIFIED on chat and identification start")
  void unverifiedBlockedFromNewAiWork() {
    String jwt = register(uniqueEmail());

    ResponseEntity<Map> chat = post("/api/v1/chat", Map.of("message", "Hello"), jwt);
    ResponseEntity<Map> identify = postImage(jwt);

    assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(chat.getBody().get("code")).isEqualTo("EMAIL_NOT_VERIFIED");
    assertThat(identify.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(identify.getBody().get("code")).isEqualTo("EMAIL_NOT_VERIFIED");
  }

  // --- helpers -----------------------------------------------------------------------------

  private String register(String email) {
    var request =
        Map.of("email", email, "password", PASSWORD, "firstName", "Verif", "lastName", "Test");
    ResponseEntity<Map> response = post("/api/v1/auth/register", request, null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) data(response).get("token");
  }

  /** Plants a token with a known value; the emailed one is not recoverable from the outbox. */
  private String plantToken(String email, Instant expiresAt) {
    String token = UUID.randomUUID().toString().replace("-", "") + "x";
    jdbc.update(
        "INSERT INTO email_verification_tokens(user_id, token_hash, email, expires_at, created_at) "
            + "SELECT id, ?, email, ?, NOW() - INTERVAL '5 minutes' FROM users WHERE email = ?",
        sha256(token),
        java.sql.Timestamp.from(expiresAt),
        email);
    return token;
  }

  private boolean isVerified(String email) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT email_verified_at IS NOT NULL FROM users WHERE email = ?",
            Boolean.class,
            email));
  }

  private int outboxCount(String email) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM email_outbox WHERE recipient = ?", Integer.class, email);
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

  private ResponseEntity<Map> post(String path, Object body, String jwt) {
    HttpHeaders headers = new HttpHeaders();
    if (jwt != null) headers.setBearerAuth(jwt);
    return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
  }

  private ResponseEntity<Map> get(String path, String jwt) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt);
    return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
  }

  private ResponseEntity<Map> postImage(String jwt) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "images",
        new ByteArrayResource(new byte[] {1, 2, 3}) {
          @Override
          public String getFilename() {
            return "test.jpg";
          }
        });
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return rest.exchange(
        url("/api/v1/identifications/analyze"),
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        Map.class);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private static String uniqueEmail() {
    return "verify-" + UUID.randomUUID() + "@example.com";
  }
}
