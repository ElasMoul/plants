package com.plantpal.dashboard.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.dashboard.dto.DashboardResponse;
import com.plantpal.dashboard.dto.PlantHealthTrendDto;
import com.plantpal.dashboard.dto.ReminderSummaryDto;
import com.plantpal.dashboard.service.impl.DashboardServiceImpl;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService — Unit Tests")
class DashboardServiceTest {

  @Mock private PlantRepository plantRepository;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private ReminderRepository reminderRepository;

  private static final Long USER_ID = 1L;
  private static final ZoneId ZONE = ZoneOffset.UTC;
  // Fixed "now": noon UTC on 2026-06-18.
  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-18T12:00:00Z"), ZONE);

  private DashboardServiceImpl dashboardService;

  @BeforeEach
  void setUp() {
    dashboardService =
        new DashboardServiceImpl(
            plantRepository, identificationRepository, reminderRepository, FIXED_CLOCK);
  }

  private Plant plant(Long id, String nickname) {
    return Plant.builder()
        .id(id)
        .userId(USER_ID)
        .nickname(nickname)
        .photoUrl("/photos/" + id + ".jpg")
        .status(PlantStatus.ACTIVE)
        .build();
  }

  private void stubNoTrendHistory(List<Plant> plants) {
    for (Plant plant : plants) {
      when(identificationRepository.findByPlantIdOrderByCreatedAtDesc(
              eq(plant.getId()), any(Pageable.class)))
          .thenReturn(Page.empty());
    }
  }

  private void stubNoRecentActivity() {
    when(identificationRepository.findByUserIdOrderByCreatedAtDesc(
            eq(USER_ID), any(Pageable.class)))
        .thenReturn(Page.empty());
    when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
            eq(USER_ID), eq(PlantStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(Page.empty());
  }

  @Nested
  @DisplayName("getDashboard() — health summary")
  class HealthSummary {

    @Test
    @DisplayName(
        "should count HEALTHY/ISSUES_DETECTED from latest identification and treat missing or"
            + " UNKNOWN as unknown")
    void shouldCountHealthStatuses() {
      Plant healthyPlant = plant(1L, "Healthy Fern");
      Plant issuesPlant = plant(2L, "Sick Cactus");
      Plant noIdentificationPlant = plant(3L, "Mystery Plant");
      Plant explicitUnknownPlant = plant(4L, "Unscanned Plant");
      List<Plant> plants =
          List.of(healthyPlant, issuesPlant, noIdentificationPlant, explicitUnknownPlant);

      when(plantRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(PlantStatus.ACTIVE), any()))
          .thenReturn(new PageImpl<>(plants));

      when(identificationRepository.findLatestPerPlant(any()))
          .thenReturn(
              List.of(
                  Identification.builder().id(10L).plantId(1L).healthStatus("HEALTHY").build(),
                  Identification.builder()
                      .id(11L)
                      .plantId(2L)
                      .healthStatus("ISSUES_DETECTED")
                      .build(),
                  Identification.builder().id(12L).plantId(4L).healthStatus("UNKNOWN").build()));

      when(reminderRepository.findByUserIdAndEnabledTrue(USER_ID)).thenReturn(List.of());
      stubNoTrendHistory(plants);
      stubNoRecentActivity();

      DashboardResponse response = dashboardService.getDashboard(USER_ID);

      assertThat(response.getHealthSummary().getTotalPlants()).isEqualTo(4);
      assertThat(response.getHealthSummary().getHealthyCount()).isEqualTo(1);
      assertThat(response.getHealthSummary().getIssuesCount()).isEqualTo(1);
      assertThat(response.getHealthSummary().getUnknownCount()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("getDashboard() — reminder partitioning")
  class ReminderPartitioning {

    @Test
    @DisplayName("should bucket reminders into overdue / today, excluding future-due reminders")
    void shouldPartitionRemindersByDueDate() {
      Plant overduePlant = plant(1L, "Overdue Plant");
      Plant todayPlant = plant(2L, "Today Plant");
      Plant futurePlant = plant(3L, "Future Plant");
      List<Plant> plants = List.of(overduePlant, todayPlant, futurePlant);

      when(plantRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(PlantStatus.ACTIVE), any()))
          .thenReturn(new PageImpl<>(plants));
      when(identificationRepository.findLatestPerPlant(any())).thenReturn(List.of());
      stubNoTrendHistory(plants);
      stubNoRecentActivity();

      Reminder overdueReminder =
          Reminder.builder()
              .id(100L)
              .plantId(1L)
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .nextDueAt(Instant.parse("2026-06-17T08:00:00Z")) // yesterday — 1 day overdue
              .enabled(true)
              .build();
      Reminder todayReminder =
          Reminder.builder()
              .id(101L)
              .plantId(2L)
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .nextDueAt(Instant.parse("2026-06-18T20:00:00Z")) // later today
              .enabled(true)
              .build();
      Reminder futureReminder =
          Reminder.builder()
              .id(102L)
              .plantId(3L)
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .nextDueAt(Instant.parse("2026-06-19T08:00:00Z")) // tomorrow
              .enabled(true)
              .build();
      when(reminderRepository.findByUserIdAndEnabledTrue(USER_ID))
          .thenReturn(List.of(overdueReminder, todayReminder, futureReminder));

      DashboardResponse response = dashboardService.getDashboard(USER_ID);

      assertThat(response.getOverdueReminders()).hasSize(1);
      ReminderSummaryDto overdueDto = response.getOverdueReminders().get(0);
      assertThat(overdueDto.getReminderId()).isEqualTo(100L);
      assertThat(overdueDto.getPlantNickname()).isEqualTo("Overdue Plant");
      assertThat(overdueDto.getDaysOverdue()).isEqualTo(1);

      assertThat(response.getTodayReminders()).hasSize(1);
      ReminderSummaryDto todayDto = response.getTodayReminders().get(0);
      assertThat(todayDto.getReminderId()).isEqualTo(101L);
      assertThat(todayDto.getDaysOverdue()).isZero();

      assertThat(response.getOverdueReminders()).noneMatch(r -> r.getReminderId().equals(102L));
      assertThat(response.getTodayReminders()).noneMatch(r -> r.getReminderId().equals(102L));
    }

    @Test
    @DisplayName("should skip reminders whose plant is no longer active (archived)")
    void shouldSkipRemindersForArchivedPlants() {
      Plant activePlant = plant(1L, "Active Plant");
      List<Plant> plants = List.of(activePlant);

      when(plantRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(PlantStatus.ACTIVE), any()))
          .thenReturn(new PageImpl<>(plants));
      when(identificationRepository.findLatestPerPlant(any())).thenReturn(List.of());
      stubNoTrendHistory(plants);
      stubNoRecentActivity();

      Reminder archivedPlantReminder =
          Reminder.builder()
              .id(200L)
              .plantId(99L) // not in the active plants map
              .userId(USER_ID)
              .careType(CareType.WATERING)
              .nextDueAt(Instant.parse("2026-06-17T08:00:00Z"))
              .enabled(true)
              .build();
      when(reminderRepository.findByUserIdAndEnabledTrue(USER_ID))
          .thenReturn(List.of(archivedPlantReminder));

      DashboardResponse response = dashboardService.getDashboard(USER_ID);

      assertThat(response.getOverdueReminders()).isEmpty();
      assertThat(response.getTodayReminders()).isEmpty();
    }
  }

  @Nested
  @DisplayName("getDashboard() — health trends")
  class HealthTrends {

    @Test
    @DisplayName(
        "should classify IMPROVING, WORSENING, STABLE and skip plants with fewer than 2"
            + " identifications")
    void shouldClassifyTrends() {
      Plant improving = plant(1L, "Improving Plant");
      Plant worsening = plant(2L, "Worsening Plant");
      Plant stable = plant(3L, "Stable Plant");
      Plant singleScan = plant(4L, "Single Scan Plant");
      List<Plant> plants = List.of(improving, worsening, stable, singleScan);

      when(plantRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(PlantStatus.ACTIVE), any()))
          .thenReturn(new PageImpl<>(plants));
      when(identificationRepository.findLatestPerPlant(any())).thenReturn(List.of());
      when(reminderRepository.findByUserIdAndEnabledTrue(USER_ID)).thenReturn(List.of());
      stubNoRecentActivity();

      when(identificationRepository.findByPlantIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
          .thenReturn(
              new PageImpl<>(
                  List.of(
                      Identification.builder().id(1L).plantId(1L).healthStatus("HEALTHY").build(),
                      Identification.builder()
                          .id(2L)
                          .plantId(1L)
                          .healthStatus("ISSUES_DETECTED")
                          .build())));
      when(identificationRepository.findByPlantIdOrderByCreatedAtDesc(eq(2L), any(Pageable.class)))
          .thenReturn(
              new PageImpl<>(
                  List.of(
                      Identification.builder()
                          .id(3L)
                          .plantId(2L)
                          .healthStatus("ISSUES_DETECTED")
                          .build(),
                      Identification.builder()
                          .id(4L)
                          .plantId(2L)
                          .healthStatus("HEALTHY")
                          .build())));
      when(identificationRepository.findByPlantIdOrderByCreatedAtDesc(eq(3L), any(Pageable.class)))
          .thenReturn(
              new PageImpl<>(
                  List.of(
                      Identification.builder().id(5L).plantId(3L).healthStatus("HEALTHY").build(),
                      Identification.builder()
                          .id(6L)
                          .plantId(3L)
                          .healthStatus("HEALTHY")
                          .build())));
      when(identificationRepository.findByPlantIdOrderByCreatedAtDesc(eq(4L), any(Pageable.class)))
          .thenReturn(
              new PageImpl<>(
                  List.of(
                      Identification.builder()
                          .id(7L)
                          .plantId(4L)
                          .healthStatus("HEALTHY")
                          .build())));

      DashboardResponse response = dashboardService.getDashboard(USER_ID);

      assertThat(response.getHealthTrends()).hasSize(3);
      assertThat(trendFor(response.getHealthTrends(), 1L)).isEqualTo("IMPROVING");
      assertThat(trendFor(response.getHealthTrends(), 2L)).isEqualTo("WORSENING");
      assertThat(trendFor(response.getHealthTrends(), 3L)).isEqualTo("STABLE");
      assertThat(response.getHealthTrends()).noneMatch(t -> t.getPlantId().equals(4L));
    }

    private String trendFor(List<PlantHealthTrendDto> trends, Long plantId) {
      return trends.stream()
          .filter(t -> t.getPlantId().equals(plantId))
          .findFirst()
          .orElseThrow()
          .getTrend();
    }
  }

  @Nested
  @DisplayName("getDashboard() — empty garden")
  class EmptyGarden {

    @Test
    @DisplayName(
        "should return a zeroed summary and empty lists, never null, without querying further")
    void shouldReturnZeroedDashboardWhenNoPlants() {
      when(plantRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(PlantStatus.ACTIVE), any()))
          .thenReturn(Page.empty());
      stubNoRecentActivity();

      DashboardResponse response = dashboardService.getDashboard(USER_ID);

      assertThat(response.getHealthSummary().getTotalPlants()).isZero();
      assertThat(response.getHealthSummary().getHealthyCount()).isZero();
      assertThat(response.getHealthSummary().getIssuesCount()).isZero();
      assertThat(response.getHealthSummary().getUnknownCount()).isZero();
      assertThat(response.getOverdueReminders()).isNotNull().isEmpty();
      assertThat(response.getTodayReminders()).isNotNull().isEmpty();
      assertThat(response.getHealthTrends()).isNotNull().isEmpty();
      assertThat(response.getRecentScans()).isNotNull().isEmpty();
      assertThat(response.getSpeciesCount()).isZero();

      verify(identificationRepository, never()).findLatestPerPlant(any());
      verify(reminderRepository, never()).findByUserIdAndEnabledTrue(anyLong());
    }
  }

  @Nested
  @DisplayName("getDashboard() — recent scans + species count")
  class RecentScansAndSpeciesCount {

    @Test
    @DisplayName(
        "should return the 3 most recent scans newest-first, with plant nicknames"
            + " resolved and null for species-level (plantId-less) scans")
    void shouldPopulateRecentScans() {
      Plant plant = plant(1L, "Monstera");
      List<Plant> plants = List.of(plant);

      when(plantRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(PlantStatus.ACTIVE), any()))
          .thenReturn(new PageImpl<>(plants));
      when(identificationRepository.findLatestPerPlant(any())).thenReturn(List.of());
      when(reminderRepository.findByUserIdAndEnabledTrue(USER_ID)).thenReturn(List.of());
      stubNoTrendHistory(plants);

      Identification newest =
          Identification.builder()
              .id(30L)
              .plantId(1L)
              .photoUrl("/photos/30.jpg")
              .healthStatus("HEALTHY")
              .build();
      newest.setCreatedAt(Instant.parse("2026-06-20T10:00:00Z"));
      Identification speciesLevelScan =
          Identification.builder()
              .id(29L)
              .plantId(null)
              .photoUrl("/photos/29.jpg")
              .healthStatus("UNKNOWN")
              .build();
      speciesLevelScan.setCreatedAt(Instant.parse("2026-06-19T10:00:00Z"));
      when(identificationRepository.findByUserIdOrderByCreatedAtDesc(
              eq(USER_ID), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(newest, speciesLevelScan)));
      when(plantRepository.findAllById(List.of(1L))).thenReturn(plants);
      when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(Pageable.class)))
          .thenReturn(Page.empty());

      DashboardResponse response = dashboardService.getDashboard(USER_ID);

      assertThat(response.getRecentScans()).hasSize(2);
      assertThat(response.getRecentScans().get(0).getIdentificationId()).isEqualTo(30L);
      assertThat(response.getRecentScans().get(0).getPlantNickname()).isEqualTo("Monstera");
      assertThat(response.getRecentScans().get(1).getIdentificationId()).isEqualTo(29L);
      assertThat(response.getRecentScans().get(1).getPlantId()).isNull();
      assertThat(response.getRecentScans().get(1).getPlantNickname()).isNull();
    }

    @Test
    @DisplayName(
        "should count each distinct species once even when the user owns multiple"
            + " plants of it")
    void shouldCountDistinctSpeciesOnce() {
      Plant plant = plant(1L, "Monstera");
      List<Plant> plants = List.of(plant);

      when(plantRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(PlantStatus.ACTIVE), any()))
          .thenReturn(new PageImpl<>(plants));
      when(identificationRepository.findLatestPerPlant(any())).thenReturn(List.of());
      when(reminderRepository.findByUserIdAndEnabledTrue(USER_ID)).thenReturn(List.of());
      stubNoTrendHistory(plants);
      when(identificationRepository.findByUserIdOrderByCreatedAtDesc(
              eq(USER_ID), any(Pageable.class)))
          .thenReturn(Page.empty());
      when(plantRepository.findDistinctSpeciesIdsByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(7L), Pageable.ofSize(1), 1));

      DashboardResponse response = dashboardService.getDashboard(USER_ID);

      assertThat(response.getSpeciesCount()).isEqualTo(1);
    }
  }
}
