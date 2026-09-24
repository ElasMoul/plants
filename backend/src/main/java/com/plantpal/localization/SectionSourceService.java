package com.plantpal.localization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface SectionSourceService {
  String generationLanguage(String kind, Long id, Long userId);

  Map<String, JsonNode> sections(String kind, Long id, Long userId);
}
