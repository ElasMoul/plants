package com.plantpal.localization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SectionTranslationServiceImpl implements SectionTranslationService {
  private final SectionSourceService sources;
  private final TranslationTextExtractor extractor;
  private final TranslationService translations;
  private final UserRepository users;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public SectionTranslationServiceImpl(
      SectionSourceService sources,
      TranslationTextExtractor extractor,
      TranslationService translations,
      UserRepository users,
      JdbcTemplate jdbc,
      ObjectMapper mapper) {
    this.sources = sources;
    this.extractor = extractor;
    this.translations = translations;
    this.users = users;
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public View get(String kind, Long id, String section, Long userId) {
    var data = sources.sections(kind, id, userId).get(section);
    if (data == null) throw new ResourceNotFoundException("Section not found");
    var texts = texts(data);
    if (texts.isEmpty())
      throw new ResourceNotFoundException("Section contains no translatable text");
    String key = hash(userId + ":" + kind + ":" + id + ":" + section);
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM plant_text_sections WHERE id=? AND fingerprint=?",
            Integer.class,
            key,
            hash(encode(texts)));
    if (count != null && count > 0) return view(key, userId);
    Map<String, String> original = new LinkedHashMap<>();
    texts.forEach(text -> original.put(text, text));
    String generation = sources.generationLanguage(kind, id, userId);
    if ("legacy".equals(generation)) generation = "en";
    var variants = new java.util.ArrayList<Variant>();
    variants.add(new Variant("en", "READY", original));
    if (!"en".equals(generation)) variants.add(new Variant(generation, "PENDING", Map.of()));
    return new View(key, generation, language(userId), variants);
  }

  @Override
  public View translate(String kind, Long id, String section, Long userId) {
    String key = ensure(kind, id, section, userId, null);
    request(key, userId, language(userId));
    return view(key, userId);
  }

  @Override
  public void generated(String kind, Long id, Long userId) {
    generated(kind, id, userId, null);
  }

  @Override
  public void generated(String kind, Long id, Long userId, String section) {
    String snapshot = sources.generationLanguage(kind, id, userId);
    String language =
        "scan".equals(kind) && section == null && !"legacy".equals(snapshot)
            ? snapshot
            : language(userId);
    var sections = sources.sections(kind, id, userId);
    var keys = new LinkedHashMap<String, String>();
    var all = new java.util.TreeSet<String>();
    for (var entry : sections.entrySet()) {
      if (section != null && !section.equals(entry.getKey())) continue;
      List<String> texts = texts(entry.getValue());
      if (texts.isEmpty()) continue;
      String fingerprint = hash(encode(texts));
      String sectionId = hash(userId + ":" + kind + ":" + id + ":" + entry.getKey());
      Integer existing =
          jdbc.queryForObject(
              "SELECT count(*) FROM plant_text_sections WHERE id=? AND fingerprint=?",
              Integer.class,
              sectionId,
              fingerprint);
      if (existing != null && existing > 0) continue;
      String key = ensure(kind, id, entry.getKey(), userId, language);
      keys.put(key, fingerprint);
      all.addAll(texts);
    }
    if ("en".equals(language) || all.isEmpty()) return;
    // One deduplicated generation job serves all sections, rather than one AI call per card.
    var job = translations.prepare(List.copyOf(all), userId, false, language);
    if ("FAILED".equals(job.status())) job = translations.retry(job.id(), userId);
    String jobId = job.id();
    keys.forEach((key, fingerprint) -> link(key, language, jobId, fingerprint));
  }

  private String ensure(String kind, Long id, String section, Long userId, String original) {
    var data = sources.sections(kind, id, userId).get(section);
    if (data == null) throw new ResourceNotFoundException("Section not found");
    List<String> texts = texts(data);
    if (texts.isEmpty())
      throw new ResourceNotFoundException("Section contains no translatable text");
    String source = encode(texts), fingerprint = hash(source);
    String key = hash(userId + ":" + kind + ":" + id + ":" + section);
    jdbc.update(
        """
        INSERT INTO plant_text_sections(id,user_id,kind,resource_id,section_key,source_text,fingerprint,original_language)
        VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
          source_text=excluded.source_text, fingerprint=excluded.fingerprint,
          original_language=CASE WHEN plant_text_sections.fingerprint<>excluded.fingerprint
            THEN excluded.original_language ELSE plant_text_sections.original_language END
        """,
        key,
        userId,
        kind,
        id,
        section,
        source,
        fingerprint,
        original == null ? "en" : original);
    jdbc.update(
        """
        INSERT INTO plant_text_versions(section_id,language,fingerprint,status)
        VALUES (?,'en',?,'READY') ON CONFLICT(section_id,language) DO UPDATE
        SET fingerprint=excluded.fingerprint,status='READY',job_id=NULL
        """,
        key,
        fingerprint);
    return key;
  }

  private void request(String key, Long userId, String target) {
    if ("en".equals(target)) return;
    var current = view(key, userId);
    var existing = current.variants().stream().filter(v -> target.equals(v.language())).findFirst();
    if (existing.isPresent() && !"FAILED".equals(existing.get().status())) return;
    String source =
        jdbc.queryForObject(
            "SELECT source_text FROM plant_text_sections WHERE id=?", String.class, key);
    var job = translations.prepare(decodeList(source), userId, false, target);
    if ("FAILED".equals(job.status())) job = translations.retry(job.id(), userId);
    link(key, target, job.id(), hash(source));
  }

  private void link(String key, String language, String jobId, String fingerprint) {
    jdbc.update(
        """
        INSERT INTO plant_text_versions(section_id,language,fingerprint,status,job_id)
        SELECT id,?,fingerprint,'PENDING',? FROM plant_text_sections WHERE id=? AND fingerprint=?
        ON CONFLICT(section_id,language) DO UPDATE SET fingerprint=excluded.fingerprint,
        status=excluded.status,job_id=excluded.job_id
        """,
        language,
        jobId,
        key,
        fingerprint);
  }

  private View view(String key, Long userId) {
    var rows =
        jdbc.query(
            "SELECT source_text,original_language,fingerprint FROM plant_text_sections WHERE id=? AND user_id=?",
            (rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)),
            key,
            userId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Section not found");
    var row = rows.get(0);
    List<String> texts = decodeList(row.get(0));
    var versions =
        jdbc.query(
            "SELECT language,status,job_id FROM plant_text_versions WHERE section_id=? AND fingerprint=? ORDER BY language",
            (rs, n) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)},
            key,
            row.get(2));
    var variants = new java.util.ArrayList<Variant>();
    for (var version : versions) {
      Map<String, String> values = new LinkedHashMap<>();
      String status = version[1];
      if ("en".equals(version[0])) texts.forEach(t -> values.put(t, t));
      else if (version[2] != null) {
        var job = translations.get(version[2], userId);
        status = job.status();
        for (String text : texts)
          if (job.texts().containsKey(text)) values.put(text, job.texts().get(text));
      }
      variants.add(new Variant(version[0], status, values));
    }
    return new View(key, row.get(1), language(userId), variants);
  }

  private List<String> texts(com.fasterxml.jackson.databind.JsonNode data) {
    var result = new java.util.TreeSet<>(extractor.extract(data));
    result.addAll(extractor.names(data));
    return List.copyOf(result);
  }

  private String language(Long userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"))
        .getLanguage();
  }

  private String encode(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private List<String> decodeList(String value) {
    try {
      return mapper.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
