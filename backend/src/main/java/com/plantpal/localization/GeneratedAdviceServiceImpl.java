package com.plantpal.localization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GeneratedAdviceServiceImpl implements GeneratedAdviceService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public GeneratedAdviceServiceImpl(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public Long save(Long scanId, Long userId, Object content) {
    return jdbc.queryForObject(
        "INSERT INTO generated_cure_advice(identification_id,user_id,content) VALUES (?,?,?::jsonb) RETURNING id",
        Long.class,
        scanId,
        userId,
        mapper.valueToTree(content).toString());
  }

  @Override
  public JsonNode get(Long id, Long userId) {
    var rows =
        jdbc.queryForList(
            "SELECT content::text FROM generated_cure_advice WHERE id=? AND user_id=?",
            String.class,
            id,
            userId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Advice not found");
    try {
      return mapper.readTree(rows.get(0));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
