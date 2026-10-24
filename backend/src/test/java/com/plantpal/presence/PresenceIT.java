package com.plantpal.presence;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.entity.*;
import com.plantpal.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PresenceIT extends AbstractIntegrationTest {
  private final TestRestTemplate http;
  private final UserRepository users;
  private final JwtUtil jwt;
  private final JdbcTemplate jdbc;
  private final PresenceRepository repository;
  private User admin;
  private User member;

  PresenceIT(
      TestRestTemplate http,
      UserRepository users,
      JwtUtil jwt,
      JdbcTemplate jdbc,
      PresenceRepository repository) {
    this.http = http;
    this.users = users;
    this.jwt = jwt;
    this.jdbc = jdbc;
    this.repository = repository;
  }

  @BeforeEach
  void setup() {
    jdbc.update("UPDATE users SET last_active_at=NULL");
    admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
    member = createUser(UserRole.USER, UserStatus.ACTIVE);
  }

  @Test
  void windowBoundaryAndNonCountedUsers() {
    Instant now = Instant.parse("2026-10-24T10:00:00Z");
    Instant since = now.minus(Duration.ofMinutes(5));
    setActive(admin, since.plusSeconds(1));
    setActive(member, since);
    setActive(createUser(UserRole.USER, UserStatus.SUSPENDED), now);
    createUser(UserRole.USER, UserStatus.ACTIVE); // NULL last_active_at
    assertThat(repository.countActiveSince(since)).isEqualTo(1);
    assertThat(repository.countActiveSince(since.plusSeconds(1))).isZero();
  }

  @Test
  void touchIsThrottled() {
    Instant now = Instant.now();
    assertThat(repository.touch(member.getId(), now, Duration.ofSeconds(30))).isEqualTo(1);
    assertThat(repository.touch(member.getId(), now.plusSeconds(10), Duration.ofSeconds(30)))
        .isZero();
    assertThat(repository.touch(member.getId(), now.plusSeconds(31), Duration.ofSeconds(30)))
        .isEqualTo(1);
  }

  @Test
  void heartbeatThenAdminCountAndAuthorization() {
    String adminToken = jwt.generateToken(admin, admin.getId());
    String memberToken = jwt.generateToken(member, member.getId());
    assertThat(call("/api/v1/presence/heartbeat", HttpMethod.POST, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(call("/api/v1/presence/heartbeat", HttpMethod.POST, memberToken).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(call("/api/v1/admin/presence", HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(call("/api/v1/admin/presence", HttpMethod.GET, memberToken).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var ok = call("/api/v1/admin/presence", HttpMethod.GET, adminToken);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    var data = (Map<?, ?>) ok.getBody().get("data");
    assertThat(((Number) data.get("onlineCount")).longValue()).isEqualTo(1);
    assertThat(((Number) data.get("windowSeconds")).longValue()).isEqualTo(300);
    assertThat(data.get("asOf")).isNotNull();
    assertThat(data).hasSize(3);
  }

  private ResponseEntity<Map> call(String path, HttpMethod method, String token) {
    var headers = new HttpHeaders();
    if (token != null) headers.setBearerAuth(token);
    return http.exchange(path, method, new HttpEntity<>(headers), Map.class);
  }

  private void setActive(User user, Instant at) {
    jdbc.update("UPDATE users SET last_active_at=? WHERE id=?", Timestamp.from(at), user.getId());
  }

  private User createUser(UserRole role, UserStatus status) {
    return users.saveAndFlush(
        User.builder()
            .firstName("Presence")
            .lastName("PLA33")
            .email(UUID.randomUUID() + "@presence-test.local")
            .passwordHash("unused-test-password")
            .status(status)
            .role(role)
            .build());
  }
}
