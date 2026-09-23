package com.plantpal.admin.repository;

import com.plantpal.admin.dto.AdminDtos.PlantActivity;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminPlantHistoryRepository {
  private static final String HISTORY =
      """
      SELECT id, 'CARE' AS kind, care_type AS title, 'COMPLETED' AS status,
             notes AS detail, performed_at AS occurred_at
      FROM care_logs WHERE plant_id=:plant AND user_id=:owner
      UNION ALL
      SELECT id, 'TREATMENT', disease_name, status, disease_description,
             COALESCE(completed_at, started_at, created_at)
      FROM treatments WHERE plant_id=:plant AND user_id=:owner
      UNION ALL
      SELECT id, 'SCAN', COALESCE(common_name, scientific_name, 'Plant scan'), status,
             health_notes, created_at
      FROM identifications WHERE plant_id=:plant AND user_id=:owner
      UNION ALL
      SELECT id, 'ADMIN', action, 'RECORDED', NULL, created_at
      FROM admin_activity WHERE target='plant:' || CAST(:plant AS text)
      UNION ALL
      SELECT id, 'PLANT', 'Added to garden', 'RECORDED', NULL, created_at
      FROM plants WHERE id=:plant AND user_id=:owner
      """;
  private final NamedParameterJdbcTemplate jdbc;

  public AdminPlantHistoryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Page<PlantActivity> history(Long userId, Long plantId, Pageable page) {
    var params =
        Map.of(
            "owner",
            userId,
            "plant",
            plantId,
            "limit",
            page.getPageSize(),
            "offset",
            page.getOffset());
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM (" + HISTORY + ") history", params, Long.class);
    var rows =
        jdbc.query(
            "SELECT * FROM ("
                + HISTORY
                + ") history ORDER BY occurred_at DESC, kind, id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, n) ->
                new PlantActivity(
                    rs.getString("kind") + ":" + rs.getLong("id"),
                    rs.getString("kind"),
                    rs.getString("title"),
                    rs.getString("status"),
                    rs.getString("detail"),
                    rs.getTimestamp("occurred_at").toInstant()));
    return new PageImpl<>(rows, page, total == null ? 0 : total);
  }
}
