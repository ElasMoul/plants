package com.plantpal.localization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TranslationServiceImpl implements TranslationService {
  private static final Logger log = LoggerFactory.getLogger(TranslationServiceImpl.class);
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final TranslationClient client;
  private final Executor executor;

  public TranslationServiceImpl(
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      TranslationClient client,
      @Qualifier("aiTaskExecutor") Executor executor) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.client = client;
    this.executor = executor;
  }

  @Override
  public TranslationResult prepare(List<String> texts, Long userId, boolean shared, String language) {
    if (!List.of("fr", "ar").contains(language)) throw new IllegalArgumentException("Unsupported language");
    if (texts.isEmpty()) return new TranslationResult("", language, "READY", Map.of());
    String source = encode(texts);
    String id = fingerprint((shared ? "species" : userId.toString()) + ":" + language + ":v1:" + source);
    int inserted =
        jdbc.update(
            """
        INSERT INTO ai_translations(id, owner_id, language, source_text, status, created_by, updated_by)
        VALUES (?, ?, ?, ?, 'PENDING', ?, ?) ON CONFLICT DO NOTHING
        """,
            id,
            shared ? null : userId,
            language,
            source,
            userId.toString(),
            userId.toString());
    if (inserted == 1) schedule(id, texts, userId, 1, language);
    return get(id, userId);
  }

  @Override
  public TranslationResult get(String id, Long userId) {
    Row row = find(id, userId);
    if ("PENDING".equals(row.status()) && row.ageSeconds() > Duration.ofMinutes(10).toSeconds()) {
      fail(id, row.attempt());
      return new TranslationResult(id, row.language(), "FAILED", Map.of());
    }
    Map<String, String> result =
        "READY".equals(row.status()) ? decodeMap(row.translated()) : Map.of();
    return new TranslationResult(id, row.language(), row.status(), result);
  }

  @Override
  public TranslationResult retry(String id, Long userId) {
    Row row = find(id, userId);
    int changed =
        jdbc.update(
            """
        UPDATE ai_translations SET status='PENDING', attempt=attempt+1, updated_at=now(), updated_by=?
        WHERE id=? AND status='FAILED' AND attempt=? AND updated_at < now() - interval '10 seconds'
        """,
            userId.toString(),
            id,
            row.attempt());
    if (changed == 1) schedule(id, decodeList(row.source()), userId, row.attempt() + 1, row.language());
    return get(id, userId);
  }

  private Row find(String id, Long userId) {
    var rows =
        jdbc.query(
            """
        SELECT status, source_text, translated_text, attempt,
        extract(epoch from now()-updated_at) AS age, language FROM ai_translations
        WHERE id=? AND (owner_id=? OR owner_id IS NULL)
        """,
            (rs, n) ->
                new Row(
                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getLong(5), rs.getString(6)),
            id,
            userId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Translation not found");
    return rows.get(0);
  }

  private void schedule(String id, List<String> texts, Long userId, int attempt, String language) {
    try {
      executor.execute(() -> generate(id, texts, userId, attempt, language));
    } catch (RuntimeException rejected) {
      fail(id, attempt);
    }
  }

  private void generate(String id, List<String> texts, Long userId, int attempt, String language) {
    try {
      Map<String, String> result = client.translate(texts, userId, language);
      jdbc.update(
          """
          UPDATE ai_translations SET status='READY', translated_text=?, updated_at=now()
          WHERE id=? AND attempt=? AND status='PENDING'
          """,
          encode(result),
          id,
          attempt);
    } catch (Exception failed) {
      log.warn("Translation failed: id={}, type={}", id, failed.getClass().getSimpleName());
      fail(id, attempt);
    }
  }

  private void fail(String id, int attempt) {
    jdbc.update(
        "UPDATE ai_translations SET status='FAILED', updated_at=now() WHERE id=? AND attempt=? AND status='PENDING'",
        id,
        attempt);
  }

  private String fingerprint(String source) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private String encode(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception invalid) {
      throw new IllegalArgumentException("Invalid translation input", invalid);
    }
  }

  private List<String> decodeList(String value) {
    try {
      return mapper.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception invalid) {
      throw new IllegalStateException("Invalid translation source", invalid);
    }
  }

  private Map<String, String> decodeMap(String value) {
    try {
      return mapper.readValue(value, new TypeReference<Map<String, String>>() {});
    } catch (Exception invalid) {
      throw new IllegalStateException("Invalid saved translation", invalid);
    }
  }

  private record Row(
      String status, String source, String translated, int attempt, long ageSeconds, String language) {}
}
