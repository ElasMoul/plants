package com.plantpal.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.entity.*;
import com.plantpal.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminMetricsIT extends AbstractIntegrationTest {
  // Far-future days keep these figures independent of rows other ITs leave behind.
  private static final String RANGE = "?from=2034-05-10&to=2034-05-11";

  private final TestRestTemplate http;
  private final UserRepository users;
  private final JwtUtil jwt;
  private final JdbcTemplate jdbc;

  AdminMetricsIT(TestRestTemplate http, UserRepository users, JwtUtil jwt, JdbcTemplate jdbc) {
    this.http = http;
    this.users = users;
    this.jwt = jwt;
    this.jdbc = jdbc;
  }

  @Test
  @SuppressWarnings("unchecked")
  void reportsExactScanErrorAndAiUsageFiguresWithRangeEdges() {
    User admin = user(UserRole.ADMIN);
    User member = user(UserRole.USER);
    scan(member, "COMPLETED", "2034-05-10T00:00:00Z");
    scan(member, "COMPLETED", "2034-05-11T23:59:59Z");
    scan(member, "FAILED", "2034-05-11T12:00:00Z");
    scan(member, "PENDING", "2034-05-10T08:00:00Z");
    scan(member, "COMPLETED", "2034-05-09T23:59:59Z"); // before range
    scan(member, "COMPLETED", "2034-05-12T00:00:00Z"); // after range
    usage(member, "2034-05-10", 2, 5);
    usage(member, "2034-05-11", 1, 3);
    usage(member, "2034-05-12", 9, 9); // after range

    var res = get("/api/v1/admin/metrics" + RANGE, jwt.generateToken(admin, admin.getId()));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    var data = (Map<String, Object>) res.getBody().get("data");
    assertThat(data).containsEntry("from", "2034-05-10").containsEntry("to", "2034-05-11");
    assertThat((Map<String, Object>) data.get("scans"))
        .containsEntry("total", 4)
        .containsEntry("completed", 2)
        .containsEntry("failed", 1)
        .containsEntry("pending", 1);
    assertThat((Map<String, Object>) data.get("errors")).containsEntry("failedScans", 1);
    var ai = (Map<String, Object>) data.get("aiUsage");
    assertThat(ai).containsEntry("totalRequests", 8).containsEntry("scanRequests", 3);
    assertThat((List<Map<String, Object>>) ai.get("perDay"))
        .extracting(d -> d.get("date"), d -> d.get("aiRequests"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("2034-05-10", 5),
            org.assertj.core.groups.Tuple.tuple("2034-05-11", 3));
    var cost = (Map<String, Object>) data.get("cost");
    assertThat(cost.get("estimated")).isNull();
    assertThat(cost).containsEntry("basis", "rate not configured");
  }

  @Test
  void emptyRangeReturnsZeros() {
    User admin = user(UserRole.ADMIN);
    var res =
        get(
            "/api/v1/admin/metrics?from=2035-01-01&to=2035-01-02",
            jwt.generateToken(admin, admin.getId()));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().get("data").toString()).contains("total=0", "totalRequests=0");
  }

  @Test
  void rejectsReversedAndOversizedRanges() {
    User admin = user(UserRole.ADMIN);
    String token = jwt.generateToken(admin, admin.getId());
    assertThat(get("/api/v1/admin/metrics?from=2034-05-11&to=2034-05-10", token).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get("/api/v1/admin/metrics?from=2034-01-01&to=2034-12-31", token).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void onlyAdministratorsMayRead() {
    User member = user(UserRole.USER);
    assertThat(http.getForEntity("/api/v1/admin/metrics" + RANGE, Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            get("/api/v1/admin/metrics" + RANGE, jwt.generateToken(member, member.getId()))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  private void scan(User user, String status, String at) {
    jdbc.update(
        "INSERT INTO identifications(user_id,status,created_at) VALUES (?,?,?::timestamptz)",
        user.getId(),
        status,
        at);
  }

  private void usage(User user, String day, int scans, int ai) {
    jdbc.update(
        "INSERT INTO user_daily_usage(user_id,usage_date,scans,ai_requests) VALUES (?,?::date,?,?)",
        user.getId(),
        day,
        scans,
        ai);
  }

  private User user(UserRole role) {
    return users.saveAndFlush(
        User.builder()
            .firstName("Metrics")
            .lastName("Test")
            .email(UUID.randomUUID() + "@admin-test.local")
            .passwordHash("unused-test-password")
            .status(UserStatus.ACTIVE)
            .role(role)
            .build());
  }

  private ResponseEntity<Map> get(String path, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
  }
}
