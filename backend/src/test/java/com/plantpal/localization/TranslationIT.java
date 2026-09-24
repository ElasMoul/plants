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
  private final SectionTranslationService sections;
  private final GeneratedAdviceService advice;
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
      SectionTranslationService sections,
      GeneratedAdviceService advice,
      UserRepository users,
      JdbcTemplate jdbc,
      TestRestTemplate http,
      JwtUtil jwt,
      org.springframework.context.ApplicationEventPublisher events) {
    this.events = events;
    this.service = service;
    this.sections = sections;
    this.advice = advice;
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
    assertThat(response.getBody()).doesNotContainKey("localization");
    verifyNoInteractions(client);
    assertThat(get("/api/v1/identifications/" + id, other, true).getStatusCode())
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
    owner = users.saveAndFlush(owner);
    when(client.translate(anyList(), anyLong(), eq("ar")))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream().collect(Collectors.toMap(text -> text, text -> "عربي " + text));
            });
    Long generatedId =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,common_name,health_notes) VALUES (?,'COMPLETED','Peace lily','Use 5 ml.') RETURNING id",
            Long.class,
            owner.getId());
    events.publishEvent(new GeneratedPlantText("scan", generatedId, owner.getId()));
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
    owner = users.saveAndFlush(owner);
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

  @Test
  void sectionReadsNeverTranslateAndExplicitRequestsKeepAtMostThreeVersions() throws Exception {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,health_notes) VALUES (?,'COMPLETED','Section care.') RETURNING id",
            Long.class,
            owner.getId());
    owner.setLanguage("fr");
    owner = users.saveAndFlush(owner);
    var original = sections.get("scan", id, "health", owner.getId());
    assertThat(original.originalLanguage()).isEqualTo("en");
    assertThat(original.variants())
        .extracting(SectionTranslationService.Variant::language)
        .containsExactly("en");
    verifyNoInteractions(client);
    sections.translate("scan", id, "health", owner.getId());
    sectionReady(id, "fr");
    sections.translate("scan", id, "health", owner.getId());
    verify(client, times(1)).translate(anyList(), eq(owner.getId()), eq("fr"));
    owner.setLanguage("ar");
    owner = users.saveAndFlush(owner);
    assertThat(sections.get("scan", id, "health", owner.getId()).originalLanguage())
        .isEqualTo("en");
    verify(client, never()).translate(anyList(), anyLong(), eq("ar"));
    when(client.translate(anyList(), anyLong(), eq("ar")))
        .thenReturn(Map.of("Section care.", "رعاية النبات"));
    sections.translate("scan", id, "health", owner.getId());
    sectionReady(id, "ar");
    assertThat(sections.get("scan", id, "health", owner.getId()).variants()).hasSize(3);
    assertThatThrownBy(() -> sections.get("scan", id, "health", other.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    jdbc.update("UPDATE identifications SET health_notes='Changed care.' WHERE id=?", id);
    assertThat(sections.get("scan", id, "health", owner.getId()).variants()).hasSize(1);
    verify(client, times(1)).translate(anyList(), anyLong(), eq("ar"));
  }

  @Test
  void generationLanguageStaysStableWhenPreferencesChange() {
    owner.setLanguage("fr");
    owner = users.saveAndFlush(owner);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,health_notes) VALUES (?,'COMPLETED','New French care.') RETURNING id",
            Long.class,
            owner.getId());
    sections.generated("scan", id, owner.getId());
    sectionReady(id, "fr");
    owner.setLanguage("ar");
    owner = users.saveAndFlush(owner);
    sections.generated("scan", id, owner.getId());
    var view = sections.get("scan", id, "health", owner.getId());
    assertThat(view.originalLanguage()).isEqualTo("fr");
    assertThat(view.targetLanguage()).isEqualTo("ar");
    assertThat(view.variants()).hasSize(2);
  }

  @Test
  void cureAdvicePersistsOriginalAndGeneratedLanguageWithOwnerIsolation() {
    owner.setLanguage("fr");
    owner = users.saveAndFlush(owner);
    Long scanId =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status) VALUES (?,'COMPLETED') RETURNING id",
            Long.class,
            owner.getId());
    Long id = advice.save(scanId, owner.getId(), Map.of("advice", "Treat roots gently."));
    assertThat(advice.get(id, owner.getId()).path("advice").asText())
        .isEqualTo("Treat roots gently.");
    assertThatThrownBy(() -> advice.get(id, other.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    sections.generated("advice", id, owner.getId());
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(sections.get("advice", id, "advice", owner.getId()).variants())
                    .anyMatch(v -> "fr".equals(v.language()) && "READY".equals(v.status())));
    assertThat(sections.get("advice", id, "advice", owner.getId()).originalLanguage())
        .isEqualTo("fr");
  }

  @Test
  void addingOneSectionDoesNotTranslateExistingSections() throws Exception {
    owner.setLanguage("fr");
    owner = users.saveAndFlush(owner);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,common_name,health_notes) VALUES (?,'COMPLETED','Old name','New notes') RETURNING id",
            Long.class,
            owner.getId());
    sections.generated("scan", id, owner.getId(), "health");
    sectionReady(id, "fr");
    assertThat(sections.get("scan", id, "name", owner.getId()).variants()).hasSize(1);
    verify(client).translate(eq(List.of("New notes")), eq(owner.getId()), eq("fr"));
  }

  @Test
  void newArabicScanIsPendingInArabicBeforeTheListenerAndKeepsItsCapturedLanguage()
      throws Exception {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,content_language,health_notes) VALUES (?,'COMPLETED','ar','New Arabic scan.') RETURNING id",
            Long.class,
            owner.getId());
    var pending = sections.get("scan", id, "health", owner.getId());
    assertThat(pending.originalLanguage()).isEqualTo("ar");
    assertThat(pending.variants())
        .anyMatch(v -> "ar".equals(v.language()) && "PENDING".equals(v.status()));
    verifyNoInteractions(client);
    when(client.translate(anyList(), anyLong(), eq("ar")))
        .thenReturn(Map.of("New Arabic scan.", "فحص جديد بالعربية."));
    sections.generated("scan", id, owner.getId());
    sectionReady(id, "ar");
    assertThat(sections.get("scan", id, "health", owner.getId()).originalLanguage())
        .isEqualTo("ar");
  }

  @Test
  void englishScanSnapshotIsNotChangedByLaterArabicPreference() {
    owner.setLanguage("ar");
    owner = users.saveAndFlush(owner);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO identifications(user_id,status,content_language,health_notes) VALUES (?,'COMPLETED','en','English scan.') RETURNING id",
            Long.class,
            owner.getId());
    sections.generated("scan", id, owner.getId());
    assertThat(sections.get("scan", id, "health", owner.getId()).originalLanguage())
        .isEqualTo("en");
    verifyNoInteractions(client);
  }

  private void sectionReady(Long id, String language) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(sections.get("scan", id, "health", owner.getId()).variants())
                    .anyMatch(v -> language.equals(v.language()) && "READY".equals(v.status())));
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
