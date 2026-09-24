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
  private final org.springframework.context.ApplicationEventPublisher events;
  @MockBean private TranslationClient client;
  private User owner;
  private User other;

  TranslationIT(
      TranslationService service,
      UserRepository users,
      JdbcTemplate jdbc,
      TestRestTemplate http,
      JwtUtil jwt,
      org.springframework.context.ApplicationEventPublisher events) {
    this.events = events;
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
    doReturn(Map.of("Retry source", "Texte traduit"))
        .when(client)
        .translate(anyList(), anyLong(), eq("fr"));
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
    jdbc.update(
        "UPDATE ai_translations SET status='FAILED', updated_at=now()-interval '20 seconds' WHERE id=?",
        arabic.id());
    service.retry(arabic.id(), owner.getId());
    ready(arabic.id(), owner);
    verify(client, times(2)).translate(anyList(), eq(owner.getId()), eq("ar"));
  }

  @Test
  void newArabicContentIsSavedBeforeReadAndReusedForSubsetsIncludingNames() throws Exception {
    owner.setLanguage("ar");
    users.saveAndFlush(owner);
    when(client.translate(anyList(), anyLong(), eq("ar")))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream().collect(Collectors.toMap(text -> text, text -> "عربي " + text));
            });
    events.publishEvent(
        new GeneratedPlantText(
            owner.getId(),
            Map.of(
                "commonName",
                "Peace lily",
                "description",
                "Keep away from pets.",
                "steps",
                List.of(Map.of("instruction", "Use 5 ml.", "detail", "Repeat in 7 days.")))));
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        service.cached(
                            List.of("Peace lily", "Use 5 ml."), owner.getId(), false, "ar"))
                    .containsKeys("Peace lily", "Use 5 ml."));
    var subset = service.prepare(List.of("Use 5 ml."), owner.getId(), false, "ar");
    assertThat(subset.status()).isEqualTo("READY");
    verify(client, times(1)).translate(anyList(), eq(owner.getId()), eq("ar"));
    assertThat(service.cached(List.of("Peace lily"), other.getId(), false, "ar")).isEmpty();
    assertThat(service.cached(List.of("Peace lily"), owner.getId(), false, "fr")).isEmpty();
  }

  @Test
  void registrationAndPreferenceUpdatesPersistLanguageWithoutChangingExistingPlants() {
    String email = UUID.randomUUID() + "@language.test";
    var registration =
        http.postForEntity(
            "/api/v1/auth/register",
            Map.of(
                "email",
                email,
                "password",
                "SafePassword123!",
                "firstName",
                "Test",
                "lastName",
                "Reader",
                "language",
                "ar"),
            Map.class);
    assertThat(registration.getStatusCode().is2xxSuccessful()).isTrue();
    User registered = users.findByEmail(email).orElseThrow();
    assertThat(registered.getLanguage()).isEqualTo("ar");
    assertThat(((Map<?, ?>) registration.getBody().get("data")).get("language")).isEqualTo("ar");
    var headers = new HttpHeaders();
    headers.setBearerAuth(jwt.generateToken(registered, registered.getId()));
    var update =
        http.exchange(
            "/api/v1/users/me/preferences",
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("language", "fr"), headers),
            Map.class);
    assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(users.findById(registered.getId()).orElseThrow().getLanguage()).isEqualTo("fr");
    var invalid =
        http.exchange(
            "/api/v1/users/me/preferences",
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("language", "xx"), headers),
            Map.class);
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(service.cached(List.of("Peace lily"), other.getId(), false, "ar")).isEmpty();
  }

  @Test
  void scanCompletionPersistsArabicWithoutOpeningItOrBackfillingOlderScans() throws Exception {
    owner.setLanguage("ar");
    users.saveAndFlush(owner);
    when(client.translate(anyList(), anyLong(), eq("ar")))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream().collect(Collectors.toMap(text -> text, text -> "عربي " + text));
            });
    Long oldId =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,health_notes) VALUES (?,'COMPLETED','Old care notes.') RETURNING id",
            Long.class,
            owner.getId());
    Long newId =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,common_name,health_notes,care_plan) VALUES (?,'COMPLETED','Peace lily','New care notes.',?::jsonb) RETURNING id",
            Long.class,
            owner.getId(),
            "{\"careCards\":[{\"title\":\"Water roots\",\"actionPlan\":{\"steps\":[{\"instruction\":\"Apply 5 ml.\"}]}}]}");
    events.publishEvent(
        com.plantpal.identification.event.IdentificationCompletedEvent.builder()
            .identificationId(newId)
            .status("COMPLETED")
            .build());
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        service.cached(
                            List.of("Peace lily", "Apply 5 ml."), owner.getId(), false, "ar"))
                    .containsKeys("Peace lily", "Apply 5 ml."));
    assertThat(service.cached(List.of("Old care notes."), owner.getId(), false, "ar")).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT health_notes FROM identifications WHERE id=?", String.class, oldId))
        .isEqualTo("Old care notes.");
    assertThat(
            jdbc.queryForObject(
                "SELECT common_name FROM identifications WHERE id=?", String.class, newId))
        .isEqualTo("Peace lily");
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
