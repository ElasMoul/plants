package com.plantpal.localization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface SectionSourceService {
  Map<String, JsonNode> sections(String kind, Long id, Long userId);
}
