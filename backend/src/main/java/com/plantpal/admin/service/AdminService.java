package com.plantpal.admin.service;

import com.plantpal.admin.dto.AdminDtos.Activity;
import com.plantpal.admin.dto.AdminDtos.Metrics;
import com.plantpal.admin.dto.AdminDtos.Overview;
import com.plantpal.admin.dto.AdminDtos.UserUpdate;
import com.plantpal.admin.dto.AdminDtos.UserView;
import com.plantpal.admin.dto.AdminDtos.VerdictCounts;
import com.plantpal.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {
  Overview overview();

  Page<UserView> users(String query, UserStatus status, Pageable pageable);

  UserView user(Long id);

  UserView updateUser(Long id, UserUpdate request, Long actorId);

  Page<Activity> activity(Pageable pageable);

  /** Operational metrics over inclusive UTC days; null bounds default to the last 30 days. */
  Metrics metrics(java.time.LocalDate from, java.time.LocalDate to);

  /** Inclusive calendar days, UTC. */
  VerdictCounts verdictCounts(java.time.LocalDate from, java.time.LocalDate to);
}
