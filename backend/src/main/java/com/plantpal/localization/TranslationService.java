package com.plantpal.localization;

import java.util.List;

public interface TranslationService {
  TranslationResult prepare(List<String> texts, Long userId, boolean shared, String language);

  TranslationResult get(String id, Long userId);

  TranslationResult retry(String id, Long userId);
}
