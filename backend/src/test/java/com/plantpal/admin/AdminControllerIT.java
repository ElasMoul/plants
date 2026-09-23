package com.plantpal.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.admin.repository.AiModelSettingRepository;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.entity.*;
import com.plantpal.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminControllerIT extends AbstractIntegrationTest {
  private final TestRestTemplate http;
  private final UserRepository users;
  private final AiModelSettingRepository models;
  private final JwtUtil jwt;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private User admin;
  private User member;
  private String adminToken;
  private String memberToken;

  AdminControllerIT(
      TestRestTemplate http,
      UserRepository users,
      AiModelSettingRepository models,
      JwtUtil jwt,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.http = http;
    this.users = users;
    this.models = models;
    this.jwt = jwt;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void setup() {
    admin = createUser(UserRole.ADMIN);
    member = createUser(UserRole.USER);
    adminToken = jwt.generateToken(admin, admin.getId());
    memberToken = jwt.generateToken(member, member.getId());
  }

  @Test
  void plantViewerIncludesCompletedTreatmentCareAndScansWithBoundedOwnerScopedHistory() {
    var created =
        call("/api/v1/plants", HttpMethod.POST, memberToken, Map.of("nickname", "History fern"));
    var id = ((Number) data(created).get("id")).longValue();
    jdbc.update(
        "INSERT INTO treatments(plant_id,user_id,disease_name,status,completed_at,disease_description) VALUES (?,?,'Leaf spot','COMPLETED',now(),'Leaves recovered')",
        id,
        member.getId());
    jdbc.update(
        "INSERT INTO care_logs(plant_id,user_id,care_type,notes,performed_at) VALUES (?,?,'WATERING','Watered well',now())",
        id,
        member.getId());
    jdbc.update(
        "INSERT INTO identifications(plant_id,user_id,status,common_name,health_notes) VALUES (?,?,'COMPLETED','Fern','Healthy foliage')",
        id,
        member.getId());
    String path = "/api/v1/admin/users/" + member.getId() + "/plants/" + id;
    var result = data(call(path + "?size=500", HttpMethod.GET, adminToken, null));
    var history = (Map<?, ?>) result.get("activity");
    assertThat(history.get("size")).isEqualTo(50);
    assertThat(history.get("totalElements")).isEqualTo(4);
    assertThat(history.get("content").toString())
        .contains("Leaf spot", "COMPLETED", "Watered well", "Healthy foliage");
    assertThat(call(path, HttpMethod.GET, memberToken, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    "/api/v1/admin/users/" + admin.getId() + "/plants/" + id,
                    HttpMethod.GET,
                    adminToken,
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    call(path, HttpMethod.DELETE, adminToken, null);
    assertThat(call(path, HttpMethod.GET, adminToken, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var page =
        (Map<?, ?>)
            data(call(path + "?size=1&page=1", HttpMethod.GET, adminToken, null)).get("activity");
    assertThat((java.util.List<?>) page.get("content")).hasSize(1);
  }

  @Test
  void archivedAccountsCanBeRestoredAndLimitsAreEditable() {
    var payload = new java.util.HashMap<String, Object>(update(member, "USER", "ARCHIVED"));
    payload.put("maxPlants", 0);
    payload.put("dailyScanLimit", 0);
    payload.put("dailyAiLimit", 0);
    var saved = call("/api/v1/admin/users/" + member.getId(), HttpMethod.PUT, adminToken, payload);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(saved).get("maxPlants")).isEqualTo(0);
    assertThat(call("/api/v1/users/me/access", HttpMethod.GET, memberToken, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    member = users.findById(member.getId()).orElseThrow();
    assertThat(member.getStatus()).isEqualTo(UserStatus.ARCHIVED);
    call(
        "/api/v1/admin/users/" + member.getId(),
        HttpMethod.PUT,
        adminToken,
        update(member, "USER", "ACTIVE"));
    assertThat(
            call("/api/v1/chat", HttpMethod.POST, memberToken, Map.of("message", "Hello"))
                .getStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(
            call("/api/v1/plants", HttpMethod.POST, memberToken, Map.of("nickname", "Fern"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    member = users.findById(member.getId()).orElseThrow();
    payload = new java.util.HashMap<>(update(member, "USER", "ACTIVE"));
    payload.put("maxPlants", -1);
    assertThat(
            call("/api/v1/admin/users/" + member.getId(), HttpMethod.PUT, adminToken, payload)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void administratorManagesPlantsWithOwnerScopeAndSoftArchive() {
    var plant = call("/api/v1/plants", HttpMethod.POST, memberToken, Map.of("nickname", "Fern"));
    assertThat(plant.getStatusCode().is2xxSuccessful()).isTrue();
    var id = data(plant).get("id");
    String path = "/api/v1/admin/users/" + member.getId() + "/plants/" + id;
    assertThat(
            call(path, HttpMethod.PUT, memberToken, Map.of("nickname", "Changed")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    path,
                    HttpMethod.PUT,
                    adminToken,
                    Map.of("nickname", "Office fern", "location", "Desk"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(
                    "/api/v1/admin/users/" + admin.getId() + "/plants/" + id,
                    HttpMethod.PUT,
                    adminToken,
                    Map.of("nickname", "Wrong owner"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(path, HttpMethod.DELETE, adminToken, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(call("/api/v1/plants/" + id, HttpMethod.GET, memberToken, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(path + "/restore", HttpMethod.POST, adminToken, Map.of()).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            data(call("/api/v1/plants/" + id, HttpMethod.GET, memberToken, null)).get("nickname"))
        .isEqualTo("Office fern");
  }

  @Test
  void requiresAdministratorForReadsAndWrites() {
    for (String path : new String[] {"overview", "users", "models", "activity"}) {
      assertThat(call("/api/v1/admin/" + path, HttpMethod.GET, memberToken, null).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(
            call(
                    "/api/v1/admin/users/" + member.getId(),
                    HttpMethod.PUT,
                    memberToken,
                    update(member, "ADMIN", "ACTIVE"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    "/api/v1/admin/models/VISION:PLANTNET",
                    HttpMethod.PUT,
                    memberToken,
                    Map.of("visible", false, "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.getForEntity("/api/v1/admin/overview", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void returnsRealOverviewAndBoundedSearch() {
    var overview = call("/api/v1/admin/overview", HttpMethod.GET, adminToken, null);
    assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((java.util.List<?>) data(overview).get("scans")).hasSize(14);
    var result =
        call(
            "/api/v1/admin/users?size=500&query=" + member.getEmail(),
            HttpMethod.GET,
            adminToken,
            null);
    assertThat(data(result).get("size")).isEqualTo(100);
    assertThat(((Number) data(result).get("totalElements")).longValue()).isEqualTo(1);
  }

  @Test
  void suspensionRejectsPreviouslyIssuedTokenAndReactivationRestoresAccess() {
    var response =
        call(
            "/api/v1/admin/users/" + member.getId(),
            HttpMethod.PUT,
            adminToken,
            update(member, "USER", "SUSPENDED"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            call("/api/v1/users/me/preferences", HttpMethod.GET, memberToken, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    member = users.findById(member.getId()).orElseThrow();
    assertThat(
            call(
                    "/api/v1/admin/users/" + member.getId(),
                    HttpMethod.PUT,
                    adminToken,
                    update(member, "USER", "ACTIVE"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call("/api/v1/users/me/preferences", HttpMethod.GET, memberToken, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void protectsSelfAccessAndRejectsStaleEdits() {
    assertThat(
            call(
                    "/api/v1/admin/users/" + admin.getId(),
                    HttpMethod.PUT,
                    adminToken,
                    update(admin, "USER", "ACTIVE"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    "/api/v1/admin/users/" + admin.getId(),
                    HttpMethod.PUT,
                    adminToken,
                    update(admin, "ADMIN", "SUSPENDED"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var payload = update(member, "ADMIN", "ACTIVE");
    assertThat(
            call("/api/v1/admin/users/" + member.getId(), HttpMethod.PUT, adminToken, payload)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call("/api/v1/admin/users/" + member.getId(), HttpMethod.PUT, adminToken, payload)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(call("/api/v1/admin/overview", HttpMethod.GET, memberToken, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @SuppressWarnings("unchecked")
  void modelVisibilityPersistsReachesPreferencesAndBlocksNewSelection() {
    var model = models.findById("VISION:PLANTNET").orElseThrow();
    boolean original = model.isVisible();
    try {
      var response =
          call(
              "/api/v1/admin/models/VISION:PLANTNET",
              HttpMethod.PUT,
              adminToken,
              Map.of("visible", false, "version", model.getVersion()));
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      var prefs = data(call("/api/v1/users/me/preferences", HttpMethod.GET, memberToken, null));
      assertThat((Map<String, Boolean>) prefs.get("visionModelVisibility"))
          .containsEntry("PLANTNET", false);
      assertThat(
              call(
                      "/api/v1/users/me/preferences",
                      HttpMethod.PUT,
                      memberToken,
                      Map.of("visionModelPreference", "PLANTNET"))
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(
              call(
                      "/api/v1/admin/models/VISION:PLANTNET",
                      HttpMethod.PUT,
                      adminToken,
                      Map.of("visible", true, "version", model.getVersion()))
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
      var history = data(call("/api/v1/admin/activity", HttpMethod.GET, adminToken, null));
      assertThat((java.util.List<?>) history.get("content")).isNotEmpty();
    } finally {
      var current = models.findById(model.getId()).orElseThrow();
      current.setVisible(original);
      models.saveAndFlush(current);
    }
  }

  private User createUser(UserRole role) {
    return users.saveAndFlush(
        User.builder()
            .firstName("AdminTest")
            .lastName("Grower")
            .email(UUID.randomUUID() + "@admin-test.local")
            .passwordHash("unused-test-password")
            .status(UserStatus.ACTIVE)
            .role(role)
            .build());
  }

  private Map<String, Object> update(User user, String role, String status) {
    return Map.of(
        "firstName",
        user.getFirstName(),
        "lastName",
        user.getLastName(),
        "role",
        role,
        "status",
        status,
        "businessTier",
        true,
        "version",
        user.getVersion());
  }

  private ResponseEntity<Map> call(String path, HttpMethod method, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> data(ResponseEntity<Map> response) {
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return (Map<String, Object>) response.getBody().get("data");
  }
}
