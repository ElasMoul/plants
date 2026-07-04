package com.plantpal.dashboard.service;

import com.plantpal.dashboard.dto.DashboardResponse;

public interface DashboardService {

  DashboardResponse getDashboard(Long userId);
}
