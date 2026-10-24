package com.plantpal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminActivityAppendOnlyIT extends AbstractIntegrationTest {
  private final TestRestTemplate http;
  private final UserRepository users;
  private final AdminRepository audit;
  private final JwtUtil jwt;
  private final JdbcTemplate jdbc;

  AdminActivityAppendOnlyIT(
      TestRestTemplate http,
      UserRepository users,
      AdminRepository audit,
      JwtUtil jwt,
      JdbcTemplate jdbc) {
    this.http = http;
    this.users = users;
    this.audit = audit;
    this.jwt = jwt;
    this.jdbc = jdbc;
  }

  @Test
  void databaseRejectsUpdateAndDeleteAndApiOffersOnlyRead() {
    User admin =
        users.saveAndFlush(
            User.builder()
                .firstName("Audit")
                .lastName("PLA36")
                .email(UUID.randomUUID() + "@audit-test.local")
                .passwordHash("unused-test-password")
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build());
    String target = "user:" + UUID.randomUUID();
    audit.record(admin.getId(), "Append-only probe", target);
    Long id =
        jdbc.queryForObject("SELECT id FROM admin_activity WHERE target = ?", Long.class, target);

    assertThatThrownBy(
            () -> jdbc.update("UPDATE admin_activity SET action = 'tampered' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM admin_activity WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThat(
            jdbc.queryForObject("SELECT action FROM admin_activity WHERE id = ?", String.class, id))
        .isEqualTo("Append-only probe");
    assertThat(
            jdbc.queryForObject(
                "SELECT created_at IS NOT NULL FROM admin_activity WHERE id = ?",
                Boolean.class,
                id))
        .isTrue();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt.generateToken(admin, admin.getId()));
    var get =
        http.exchange(
            "/api/v1/admin/activity?size=100",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class);
    assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (String path : new String[] {"/api/v1/admin/activity", "/api/v1/admin/activity/" + id}) {
      for (HttpMethod method : new HttpMethod[] {HttpMethod.PUT, HttpMethod.DELETE}) {
        var response = http.exchange(path, method, new HttpEntity<>(Map.of(), headers), Map.class);
        // The global handler may surface an unmapped method as 500; what matters is no success.
        assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
      }
    }
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM admin_activity WHERE id = ?", Long.class, id))
        .isEqualTo(1L);
  }
}
