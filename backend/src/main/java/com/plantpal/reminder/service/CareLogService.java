package com.plantpal.reminder.service;

import com.plantpal.reminder.dto.CareLogResponse;
import com.plantpal.reminder.dto.MarkCareDoneRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CareLogService {

  CareLogResponse logCare(MarkCareDoneRequest request, Long userId);

  Page<CareLogResponse> getPlantCareLogs(Long plantId, Long userId, Pageable pageable);
}
