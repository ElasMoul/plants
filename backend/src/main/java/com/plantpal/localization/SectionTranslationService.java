package com.plantpal.localization;

import java.util.List;
import java.util.Map;

public interface SectionTranslationService {
  View get(String kind, Long id, String section, Long userId);

  View translate(String kind, Long id, String section, Long userId);

  void generated(String kind, Long id, Long userId);

  void generated(String kind, Long id, Long userId, String section);

  record Variant(String language, String status, Map<String, String> texts) {}

  record View(String id, String originalLanguage, String targetLanguage, List<Variant> variants) {}
}
