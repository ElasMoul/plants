package com.plantpal.dashboard.service.impl;

import com.plantpal.dashboard.dto.DashboardResponse;
import com.plantpal.dashboard.dto.HealthSummaryDto;
import com.plantpal.dashboard.dto.PlantHealthTrendDto;
import com.plantpal.dashboard.dto.ReminderSummaryDto;
import com.plantpal.dashboard.service.DashboardService;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {

  private static final int MAX_ACTIVE_PLANTS = 200;
  private static final String HEALTHY = "HEALTHY";
  private static final String ISSUES_DETECTED = "ISSUES_DETECTED";

  private final PlantRepository plantRepository;
  private final IdentificationRepository identificationRepository;
  private final ReminderRepository reminderRepository;
  private final Clock clock;

  public DashboardServiceImpl(
      PlantRepository plantRepository,
      IdentificationRepository identificationRepository,
      ReminderRepository reminderRepository,
      Clock clock) {
    this.plantRepository = plantRepository;
    this.identificationRepository = identificationRepository;
    this.reminderRepository = reminderRepository;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public DashboardResponse getDashboard(Long userId) {
    List<Plant> plants =
        plantRepository
            .findAllByUserIdAndStatus(
                userId, PlantStatus.ACTIVE, PageRequest.of(0, MAX_ACTIVE_PLANTS))
            .getContent();

    if (plants.isEmpty()) {
      return DashboardResponse.builder()
          .healthSummary(HealthSummaryDto.builder().build())
          .overdueReminders(List.of())
          .todayReminders(List.of())
          .healthTrends(List.of())
          .build();
    }

    List<Long> plantIds = plants.stream().map(Plant::getId).toList();
    Map<Long, Plant> plantsById = plants.stream().collect(Collectors.toMap(Plant::getId, p -> p));

    HealthSummaryDto healthSummary = buildHealthSummary(plants, plantIds);
    List<Reminder> reminders = reminderRepository.findByUserIdAndEnabledTrue(userId);
    ReminderBuckets buckets = partitionReminders(reminders, plantsById);
    List<PlantHealthTrendDto> healthTrends = buildHealthTrends(plants);

    return DashboardResponse.builder()
        .healthSummary(healthSummary)
        .overdueReminders(buckets.overdue())
        .todayReminders(buckets.today())
        .healthTrends(healthTrends)
        .build();
  }

  private HealthSummaryDto buildHealthSummary(List<Plant> plants, List<Long> plantIds) {
    Map<Long, String> healthByPlantId =
        identificationRepository.findLatestPerPlant(plantIds).stream()
            .collect(Collectors.toMap(Identification::getPlantId, Identification::getHealthStatus));

    int healthy = 0;
    int issues = 0;
    int unknown = 0;
    for (Plant plant : plants) {
      String health = healthByPlantId.get(plant.getId());
      if (HEALTHY.equals(health)) {
        healthy++;
      } else if (ISSUES_DETECTED.equals(health)) {
        issues++;
      } else {
        unknown++;
      }
    }

    return HealthSummaryDto.builder()
        .totalPlants(plants.size())
        .healthyCount(healthy)
        .issuesCount(issues)
        .unknownCount(unknown)
        .build();
  }

  private ReminderBuckets partitionReminders(
      List<Reminder> reminders, Map<Long, Plant> plantsById) {
    ZoneId zone = clock.getZone();
    LocalDate today = LocalDate.now(clock);
    Instant startOfToday = today.atStartOfDay(zone).toInstant();
    Instant startOfTomorrow = startOfToday.plus(1, ChronoUnit.DAYS);

    List<Reminder> sorted =
        reminders.stream()
            .filter(r -> plantsById.containsKey(r.getPlantId()))
            .sorted(Comparator.comparing(Reminder::getNextDueAt))
            .toList();

    List<ReminderSummaryDto> overdue = new ArrayList<>();
    List<ReminderSummaryDto> todayList = new ArrayList<>();

    for (Reminder reminder : sorted) {
      Instant dueAt = reminder.getNextDueAt();
      if (dueAt.isBefore(startOfToday)) {
        overdue.add(toReminderSummary(reminder, plantsById, today, zone));
      } else if (dueAt.isBefore(startOfTomorrow)) {
        todayList.add(toReminderSummary(reminder, plantsById, today, zone));
      }
    }
    return new ReminderBuckets(overdue, todayList);
  }

  private ReminderSummaryDto toReminderSummary(
      Reminder reminder, Map<Long, Plant> plantsById, LocalDate today, ZoneId zone) {
    Plant plant = plantsById.get(reminder.getPlantId());
    LocalDate dueDate = reminder.getNextDueAt().atZone(zone).toLocalDate();
    int daysOverdue = (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, today));

    return ReminderSummaryDto.builder()
        .reminderId(reminder.getId())
        .plantId(plant.getId())
        .plantNickname(plant.getNickname())
        .plantPhotoUrl(plant.getPhotoUrl())
        .careType(reminder.getCareType().name())
        .nextDueAt(reminder.getNextDueAt())
        .daysOverdue(daysOverdue)
        .build();
  }

  private List<PlantHealthTrendDto> buildHealthTrends(List<Plant> plants) {
    List<PlantHealthTrendDto> trends = new ArrayList<>();
    for (Plant plant : plants) {
      List<Identification> recentTwo =
          identificationRepository
              .findByPlantIdOrderByCreatedAtDesc(plant.getId(), PageRequest.of(0, 2))
              .getContent();
      if (recentTwo.size() < 2) {
        continue;
      }
      String latest = recentTwo.get(0).getHealthStatus();
      String previous = recentTwo.get(1).getHealthStatus();
      trends.add(
          PlantHealthTrendDto.builder()
              .plantId(plant.getId())
              .plantNickname(plant.getNickname())
              .trend(computeTrend(previous, latest))
              .build());
    }
    return trends;
  }

  private String computeTrend(String previous, String latest) {
    if (ISSUES_DETECTED.equals(previous) && HEALTHY.equals(latest)) {
      return "IMPROVING";
    }
    if (HEALTHY.equals(previous) && ISSUES_DETECTED.equals(latest)) {
      return "WORSENING";
    }
    return "STABLE";
  }

  private record ReminderBuckets(
      List<ReminderSummaryDto> overdue, List<ReminderSummaryDto> today) {}
}
