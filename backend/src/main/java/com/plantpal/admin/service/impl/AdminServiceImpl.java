package com.plantpal.admin.service.impl;

import com.plantpal.admin.dto.AdminDtos.Activity;
import com.plantpal.admin.dto.AdminDtos.Overview;
import com.plantpal.admin.dto.AdminDtos.UserUpdate;
import com.plantpal.admin.dto.AdminDtos.UserView;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {
  private final UsageService usage;
  private final UserRepository users;
  private final AdminRepository repository;

  public AdminServiceImpl(UserRepository users, AdminRepository repository, UsageService usage) {
    this.usage = usage;
    this.users = users;
    this.repository = repository;
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
