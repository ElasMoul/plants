package com.plantpal.admin.service.impl;

import com.plantpal.admin.dto.AdminDtos.Activity;
import com.plantpal.admin.dto.AdminDtos.AiUsage;
import com.plantpal.admin.dto.AdminDtos.Cost;
import com.plantpal.admin.dto.AdminDtos.Errors;
import com.plantpal.admin.dto.AdminDtos.Metrics;
import com.plantpal.admin.dto.AdminDtos.Overview;
import com.plantpal.admin.dto.AdminDtos.UsageDay;
import com.plantpal.admin.dto.AdminDtos.UserUpdate;
import com.plantpal.admin.dto.AdminDtos.UserView;
import com.plantpal.admin.dto.AdminDtos.VerdictCounts;
import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.admin.service.AdminService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.UnauthorizedException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import com.plantpal.user.service.UsageService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {
  private static final int MAX_METRIC_DAYS = 92;

  private final UsageService usage;
  private final UserRepository users;
  private final AdminRepository repository;

  @Value("${app.admin.metrics.ai-cost-per-request:0}")
  private BigDecimal aiCostPerRequest;

  public AdminServiceImpl(UserRepository users, AdminRepository repository, UsageService usage) {
    this.usage = usage;
    this.users = users;
    this.repository = repository;
  }

  @Override
  public VerdictCounts verdictCounts(java.time.LocalDate from, java.time.LocalDate to) {
    if (to.isBefore(from)) {
      throw new com.plantpal.shared.exception.ValidationException("to must not precede from");
    }
    return repository.verdictCounts(
        from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
        to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
  }

  @Override
  public Metrics metrics(LocalDate from, LocalDate to) {
    LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
    LocalDate start = from != null ? from : end.minusDays(29);
    if (end.isBefore(start)) throw new ValidationException("to must not precede from");
    if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_METRIC_DAYS) {
      throw new ValidationException("Range must not exceed " + MAX_METRIC_DAYS + " days");
    }
    var scans =
        repository.scanCounts(
            start.atStartOfDay(ZoneOffset.UTC).toInstant(),
            end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    var days = repository.usageDays(start, end);
    long requests = days.stream().mapToLong(UsageDay::aiRequests).sum();
    long scanRequests = days.stream().mapToLong(UsageDay::scans).sum();
    return new Metrics(
        start.toString(),
        end.toString(),
        scans,
        new Errors(scans.failed()),
        new AiUsage(requests, scanRequests, days),
        cost(requests));
  }

  private Cost cost(long requests) {
    if (aiCostPerRequest == null || aiCostPerRequest.signum() <= 0) {
      return new Cost(null, null, "rate not configured");
    }
    return new Cost(
        aiCostPerRequest.multiply(BigDecimal.valueOf(requests)),
        aiCostPerRequest,
        "estimate: AI requests x configured per-request rate (no billed cost recorded)");
  }

  @Override
  public Overview overview() {
    return repository.overview();
  }

  @Override
  public Page<UserView> users(String query, UserStatus status, Pageable pageable) {
    return users.searchForAdmin(query.trim(), status, pageable).map(this::view);
  }

  @Override
  public UserView user(Long id) {
    return view(findUser(id));
  }

  @Override
  @Transactional
  public UserView updateUser(Long id, UserUpdate request, Long actorId) {
    repository.lockAccessChanges();
    User actor = findUser(actorId);
    if (actor.getRole() != UserRole.ADMIN || actor.getStatus() != UserStatus.ACTIVE) {
      throw new UnauthorizedException("Administrator access has changed. Refresh your session.");
    }
    User user =
        users
            .findForAdminUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    validateUpdate(user, request, actorId);
    String changes =
        "User updated: role "
            + user.getRole()
            + " → "
            + request.role()
            + ", status "
            + user.getStatus()
            + " → "
            + request.status();
    user.setFirstName(request.firstName().trim());
    user.setLastName(request.lastName().trim());
    user.setStatus(request.status());
    user.setRole(request.role());
    user.setBusinessTier(request.businessTier());
    if (request.maxPlants() != null) user.setMaxPlants(request.maxPlants());
    if (request.dailyScanLimit() != null) user.setDailyScanLimit(request.dailyScanLimit());
    if (request.dailyAiLimit() != null) user.setDailyAiLimit(request.dailyAiLimit());
    users.saveAndFlush(user);
    repository.record(
        actorId,
        changes
            + "; limits plants="
            + user.getMaxPlants()
            + ", scans/day="
            + user.getDailyScanLimit()
            + ", AI/day="
            + user.getDailyAiLimit(),
        "user:" + id);
    return view(user);
  }

  @Override
  public Page<Activity> activity(Pageable pageable) {
    return repository.activity(pageable);
  }

  private void validateUpdate(User user, UserUpdate request, Long actorId) {
    if (user.getVersion() != request.version()) {
      throw new ValidationException("This account has changed. Refresh before saving again.");
    }
    if (user.getId().equals(actorId)
        && (request.role() != UserRole.ADMIN || request.status() != UserStatus.ACTIVE)) {
      throw new ValidationException(
          "You cannot remove your own administrator access or suspend your own account.");
    }
  }

  private User findUser(Long id) {
    return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  private UserView view(User user) {
    return new UserView(
        user.getId(),
        user.getFirstName(),
        user.getLastName(),
        user.getEmail(),
        user.getStatus(),
        user.getRole(),
        user.isBusinessTier(),
        user.getCreatedAt(),
        user.getVisionModelPreference().name(),
        user.getReasoningModelPreference().name(),
        user.getVersion(),
        user.getMaxPlants(),
        user.getDailyScanLimit(),
        user.getDailyAiLimit(),
        usage.usage(user.getId()));
  }
}
