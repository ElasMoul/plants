package com.plantpal.admin.dto;

import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.service.UsageService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminDtos {
  private AdminDtos() {}

  public record Access(Long userId, String name, boolean administrator) {}

  public record UserView(
      Long id,
      String firstName,
      String lastName,
      String email,
      UserStatus status,
      UserRole role,
      boolean businessTier,
      Instant createdAt,
      String visionModel,
      String reasoningModel,
      long version,
      int maxPlants,
      int dailyScanLimit,
      int dailyAiLimit,
      UsageService.Usage usage) {}

  public record UserUpdate(
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @NotNull UserStatus status,
      @NotNull UserRole role,
      boolean businessTier,
      @NotNull Long version,
      @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1000000)
          Integer maxPlants,
      @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1000000)
          Integer dailyScanLimit,
      @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1000000)
          Integer dailyAiLimit) {}

  public record ModelView(
      String id,
      String capability,
      String model,
      boolean visible,
      boolean configured,
      long version) {}

  public record ModelUpdate(boolean visible, @NotNull Long version) {}

  public record Activity(Long id, Long actorId, String action, String target, Instant createdAt) {}

  public record Overview(Map<String, Long> totals, List<Day> scans, Instant refreshedAt) {}

  public record Day(String date, long completed, long failed, long pending) {}
}
