package com.plantpal.presence;

import com.plantpal.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {
  private final PresenceService presence;

  public PresenceController(PresenceService presence) {
    this.presence = presence;
  }

  @PostMapping("/heartbeat")
  public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal User user) {
    presence.heartbeat(user.getId());
    return ResponseEntity.noContent().build();
  }
}
