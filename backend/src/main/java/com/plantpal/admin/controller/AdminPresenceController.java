package com.plantpal.admin.controller;

import com.plantpal.presence.PresenceService;
import com.plantpal.presence.PresenceService.Snapshot;
import com.plantpal.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/presence")
public class AdminPresenceController {
  private final PresenceService presence;

  public AdminPresenceController(PresenceService presence) {
    this.presence = presence;
  }

  @GetMapping
  public ApiResponse<Snapshot> presence() {
    return ApiResponse.success(presence.snapshot());
  }
}
