package com.plantpal.identification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Identification date_taken migration (041)")
class IdentificationDateTakenIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;

  @Test
  @DisplayName("date_taken is NOT NULL after migration")
  void columnIsNotNull() {
    String nullable =
        jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name='identifications' AND column_name='date_taken'",
            String.class);

    assertThat(nullable).isEqualTo("NO");
  }

  @Test
  @DisplayName("backfill statement copies each row's own created_at into date_taken")
  void backfillCopiesCreatedAt() {
    User user =
        users.save(
            User.builder()
                .email(UUID.randomUUID() + "@datetaken.test")
                .passwordHash("test")
                .firstName("Date")
                .lastName("Taken")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build());
    Instant[] stamps = {
      Instant.parse("2020-01-01T10:00:00Z"),
      Instant.parse("2022-06-15T12:30:00Z"),
      Instant.parse("2024-12-31T23:59:59Z")
    };
    jdbc.execute("ALTER TABLE identifications ALTER COLUMN date_taken DROP NOT NULL");
    try {
      for (Instant stamp : stamps) {
        jdbc.update(
            "INSERT INTO identifications(user_id,status,created_at,date_taken) "
                + "VALUES (?,'COMPLETED',?,NULL)",
            user.getId(),
            Timestamp.from(stamp));
      }

      jdbc.update("UPDATE identifications SET date_taken = created_at WHERE date_taken IS NULL");

      List<Map<String, Object>> rows =
          jdbc.queryForList(
              "SELECT created_at, date_taken FROM identifications WHERE user_id = ?", user.getId());
      assertThat(rows).hasSize(3);
      assertThat(rows)
          .allSatisfy(r -> assertThat(r.get("date_taken")).isEqualTo(r.get("created_at")));
    } finally {
      jdbc.update("DELETE FROM identifications WHERE user_id = ?", user.getId());
      jdbc.execute("ALTER TABLE identifications ALTER COLUMN date_taken SET NOT NULL");
    }
  }
}
