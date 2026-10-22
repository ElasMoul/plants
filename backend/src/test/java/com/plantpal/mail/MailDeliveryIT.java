package com.plantpal.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.repository.EmailOutboxRepository;
import com.plantpal.mail.service.MailService;
import com.plantpal.mail.service.impl.EmailDispatcher;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Transactional email - integration (PostgreSQL via Testcontainers)")
class MailDeliveryIT extends AbstractIntegrationTest {

  private final MailService mail;
  private final EmailDispatcher dispatcher;
  private final EmailOutboxRepository outbox;
  private final UserRepository users;
  private final JwtUtil jwt;
  private final TestRestTemplate http;

  private String adminToken;
  private String memberToken;

  MailDeliveryIT(
      MailService mail,
      EmailDispatcher dispatcher,
      EmailOutboxRepository outbox,
      UserRepository users,
      JwtUtil jwt,
      TestRestTemplate http) {
    this.mail = mail;
    this.dispatcher = dispatcher;
    this.outbox = outbox;
    this.users = users;
    this.jwt = jwt;
    this.http = http;
  }

  @BeforeEach
  void setUp() {
    User admin = createUser(UserRole.ADMIN);
    User member = createUser(UserRole.USER);
    adminToken = jwt.generateToken(admin, admin.getId());
    memberToken = jwt.generateToken(member, member.getId());
  }

  @Test
  @DisplayName("a queued email is claimed, sent and its one-time link is wiped")
  void queuedEmailIsSent() {
    Long id =
        mail.send(
            EmailTemplate.ACCOUNT_RECOVERY,
            UUID.randomUUID() + "@mail-test.local",
            "fr",
            Map.of("name", "Sara", "link", "https://www.planotell.com/recover?token=secret-1"));

    dispatcher.dispatchDue();

    // The scheduled poller may have claimed it first; either way it must end up SENT.
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              EmailOutbox row = outbox.findById(id).orElseThrow();
              assertThat(row.getStatus()).isEqualTo(EmailStatus.SENT);
              assertThat(row.getAttempts()).isGreaterThanOrEqualTo(1);
              assertThat(row.getSentAt()).isNotNull();
              assertThat(row.getVariables()).isEqualTo("{}");
            });
  }

  @Test
  @DisplayName("operators can list failed emails; members cannot")
  void adminSeesFailures() {
    EmailOutbox failed = new EmailOutbox();
    failed.setTemplate(EmailTemplate.ACCOUNT_VERIFICATION);
    failed.setRecipient("failed-" + UUID.randomUUID() + "@mail-test.local");
    failed.setLanguage("en");
    failed.setVariables("{}");
    failed.setStatus(EmailStatus.FAILED);
    failed.setAttempts(5);
    failed.setNextAttemptAt(Instant.now());
    failed.setLastError("535 authentication failed");
    Long id = outbox.saveAndFlush(failed).getId();

    ResponseEntity<Map> asAdmin =
        call("/api/v1/admin/email-deliveries?status=FAILED&size=100", adminToken);
    ResponseEntity<Map> asMember = call("/api/v1/admin/email-deliveries", memberToken);

    assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> page = (Map<String, Object>) asAdmin.getBody().get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
    Map<String, Object> mine =
        content.stream()
            .filter(item -> ((Number) item.get("id")).longValue() == id)
            .findFirst()
            .orElseThrow();
    assertThat(mine.get("status")).isEqualTo("FAILED");
    assertThat(mine.get("lastError")).isEqualTo("535 authentication failed");
    assertThat(mine).doesNotContainKey("variables");
    assertThat(asMember.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<Map> call(String path, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
  }

  private User createUser(UserRole role) {
    return users.saveAndFlush(
        User.builder()
            .firstName("Mail")
            .lastName("Tester")
            .email(UUID.randomUUID() + "@mail-test.local")
            .passwordHash("unused-test-password")
            .status(UserStatus.ACTIVE)
            .role(role)
            .build());
  }
}
