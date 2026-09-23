package com.plantpal.localization;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.entity.*;
import com.plantpal.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class TranslationIT extends AbstractIntegrationTest {
  private final TranslationService service;
  private final UserRepository users;
  private final JdbcTemplate jdbc;
  private final TestRestTemplate http;
  private final JwtUtil jwt;
  @MockBean private TranslationClient client;
  private User owner;
  private User other;

  TranslationIT(
      TranslationService service,
      UserRepository users,
      JdbcTemplate jdbc,
      TestRestTemplate http,
      JwtUtil jwt) {
    this.service = service;
    this.users = users;
    this.jdbc = jdbc;
    this.http = http;
    this.jwt = jwt;
  }

  @BeforeEach
  void setup() throws Exception {
    owner = user();
    other = user();
    when(client.translate(anyList(), anyLong(), eq("fr")))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream()
                  .collect(Collectors.toMap(text -> text, text -> "Français : " + text));
            });
  }

  @Test
  void persistsAndDeduplicatesTranslationsButInvalidatesChangedSource() throws Exception {
    var first = service.prepare(List.of("Water every 7 days."), owner.getId(), false, "fr");
    ready(first.id(), owner);
    var cached = service.prepare(List.of("Water every 7 days."), owner.getId(), false, "fr");
    assertThat(cached.id()).isEqualTo(first.id());
    assertThat(cached.texts()).containsKey("Water every 7 days.");
    verify(client, times(1)).translate(anyList(), eq(owner.getId()), eq("fr"));
    var changed = service.prepare(List.of("Water every 14 days."), owner.getId(), false, "fr");
    assertThat(changed.id()).isNotEqualTo(first.id());
    ready(changed.id(), owner);
    assertThatThrownBy(() -> service.get(first.id(), other.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void sharesSpeciesTranslationsAcrossUsers() throws Exception {
    var first =
        service.prepare(List.of("Shared species " + UUID.randomUUID()), owner.getId(), true, "fr");
    ready(first.id(), owner);
    assertThat(service.get(first.id(), other.getId()).texts()).isNotEmpty();
    verify(client, times(1)).translate(anyList(), anyLong(), eq("fr"));
  }

  @Test
  void failureIsExplicitAndRetryUsesSameJobWithoutRepeatingDomainGeneration() throws Exception {
    when(client.translate(anyList(), anyLong(), eq("fr")))
        .thenThrow(new IllegalStateException("provider unavailable"));
    var result = service.prepare(List.of("Retry source"), owner.getId(), false, "fr");
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(service.get(result.id(), owner.getId()).status()).isEqualTo("FAILED"));
    jdbc.update(
        "UPDATE ai_translations SET updated_at=now()-interval '20 seconds' WHERE id=?",
        result.id());
    doReturn(Map.of("Retry source", "Texte traduit")).when(client).translate(anyList(), anyLong(), eq("fr"));
    service.retry(result.id(), owner.getId());
    ready(result.id(), owner);
    assertThat(service.get(result.id(), owner.getId()).texts())
        .containsEntry("Retry source", "Texte traduit");
  }

  @Test
  void domainAuthorizationPrecedesLocalizationAndCanonicalResponseStaysUntouched() {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,health_notes) VALUES (?,'COMPLETED','Water every 7 days.') RETURNING id",
            Long.class,
            owner.getId());
    var response = get("/api/v1/identifications/" + id, owner, true);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
    assertThat(data.get("healthNotes")).isEqualTo("Water every 7 days.");
    String jobId = (String) ((Map<?, ?>) response.getBody().get("localization")).get("id");
    ready(jobId, owner);
    assertThat(get("/api/v1/identifications/" + id, other, true).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/v1/translations/" + jobId, other, true).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/v1/identifications/" + id, owner, false).getBody())
        .doesNotContainKey("localization");
    assertThat(
            jdbc.queryForObject(
                "SELECT health_notes FROM identifications WHERE id=?", String.class, id))
        .isEqualTo("Water every 7 days.");
  }

  @Test
  void arabicAndFrenchHaveSeparateCachesAndRetryKeepsArabic() throws Exception {
    when(client.translate(anyList(), anyLong(), eq("ar")))
        .thenReturn(Map.of("Water every 7 days.", "اسقِ كل 7 أيام."));
    var french = service.prepare(List.of("Water every 7 days."), owner.getId(), false, "fr");
    var arabic = service.prepare(List.of("Water every 7 days."), owner.getId(), false, "ar");
    ready(french.id(), owner);
    ready(arabic.id(), owner);
    assertThat(arabic.id()).isNotEqualTo(french.id());
    assertThat(service.get(arabic.id(), owner.getId()).language()).isEqualTo("ar");
    assertThat(service.get(arabic.id(), owner.getId()).texts())
        .containsEntry("Water every 7 days.", "اسقِ كل 7 أيام.");
    jdbc.update("UPDATE ai_translations SET status='FAILED', updated_at=now()-interval '20 seconds' WHERE id=?", arabic.id());
    service.retry(arabic.id(), owner.getId());
    ready(arabic.id(), owner);
    verify(client, times(2)).translate(anyList(), eq(owner.getId()), eq("ar"));
  }

  private void ready(String id, User user) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(service.get(id, user.getId()).status()).isEqualTo("READY"));
  }

  private User user() {
    return users.save(
        User.builder()
            .email(UUID.randomUUID() + "@translation.test")
            .passwordHash("test")
            .firstName("Reader")
            .lastName("Test")
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .build());
  }

  private ResponseEntity<Map> get(String path, User user, boolean french) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(jwt.generateToken(user, user.getId()));
    if (french) headers.set("X-Content-Language", "fr");
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
  }
}
