package com.plantpal.localization;

import com.fasterxml.jackson.databind.JsonNode;

public interface GeneratedAdviceService {
  Long save(Long scanId, Long userId, Object content);

  JsonNode get(Long id, Long userId);
}
