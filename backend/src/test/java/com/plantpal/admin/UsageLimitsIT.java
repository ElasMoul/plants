package com.plantpal.admin;

import static org.assertj.core.api.Assertions.*;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.service.PlantService;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.user.entity.*;
import com.plantpal.user.repository.UserRepository;
import com.plantpal.user.service.UsageService;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UsageLimitsIT extends AbstractIntegrationTest {
  private final UserRepository users;
  private final UsageService usage;
  private final PlantService plants;
  private final JdbcTemplate jdbc;

  UsageLimitsIT(UserRepository users, UsageService usage, PlantService plants, JdbcTemplate jdbc) {
    this.users = users;
    this.usage = usage;
    this.plants = plants;
    this.jdbc = jdbc;
  }

  @Test
  void scanAndAiLimitsAreIndependentAndPersistent() {
    User user = user(10, 1, 2);
    usage.consumeAi(user.getId(), true);
    assertThatThrownBy(() -> usage.consumeAi(user.getId(), true))
        .isInstanceOf(PlantPalException.class);
    usage.consumeAi(user.getId(), false);
    assertThatThrownBy(() -> usage.consumeAi(user.getId(), false))
        .isInstanceOf(PlantPalException.class);
    assertThat(usage.usage(user.getId()).scansToday()).isEqualTo(1);
    assertThat(usage.usage(user.getId()).aiToday()).isEqualTo(2);
    jdbc.update(
        "UPDATE user_daily_usage SET usage_date=usage_date-1 WHERE user_id=?", user.getId());
    usage.consumeAi(user.getId(), true);
    assertThat(usage.usage(user.getId()).aiToday()).isEqualTo(1);
  }

  @Test
  void concurrentAttemptsCannotExceedDailyLimit() throws Exception {
    User user = user(10, 1, 1);
    try (var pool = Executors.newFixedThreadPool(6)) {
      var start = new CountDownLatch(1);
      var attempts = new java.util.ArrayList<Future<Boolean>>();
      for (int i = 0; i < 6; i++)
        attempts.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    usage.consumeAi(user.getId(), true);
                    return true;
                  } catch (PlantPalException e) {
                    return false;
                  }
                }));
      start.countDown();
      int accepted = 0;
      for (var attempt : attempts) if (attempt.get(15, TimeUnit.SECONDS)) accepted++;
      assertThat(accepted).isEqualTo(1);
      assertThat(usage.usage(user.getId()).scansToday()).isEqualTo(1);
    }
  }

  @Test
  void plantArchiveFreesCapacityAndRestoreRespectsLimit() {
    User user = user(1, 10, 10);
    var first = plants.createPlant(plant(), user.getId());
    assertThatThrownBy(() -> plants.createPlant(plant(), user.getId()))
        .isInstanceOf(PlantPalException.class);
    plants.archivePlant(first.getId(), user.getId());
    var second = plants.createPlant(plant(), user.getId());
    assertThatThrownBy(() -> plants.restorePlant(first.getId(), user.getId()))
        .isInstanceOf(PlantPalException.class);
    plants.archivePlant(second.getId(), user.getId());
    plants.restorePlant(first.getId(), user.getId());
    assertThat(usage.usage(user.getId()).plants()).isEqualTo(1);
  }

  @Test
  void concurrentPlantCreationCannotExceedCapacity() throws Exception {
    User user = user(1, 10, 10);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Boolean> create =
          () -> {
            start.await();
            try {
              plants.createPlant(plant(), user.getId());
              return true;
            } catch (PlantPalException e) {
              return false;
            }
          };
      var first = pool.submit(create);
      var second = pool.submit(create);
      start.countDown();
      assertThat(
              java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  private User user(int plants, int scans, int ai) {
    return users.saveAndFlush(
        User.builder()
            .email(UUID.randomUUID() + "@limits.test")
            .firstName("Quota")
            .lastName("Tester")
            .passwordHash("unused")
            .status(UserStatus.ACTIVE)
            .maxPlants(plants)
            .dailyScanLimit(scans)
            .dailyAiLimit(ai)
            .build());
  }

  private CreatePlantRequest plant() {
    var request = new CreatePlantRequest();
    request.setNickname("Fern");
    return request;
  }
}
