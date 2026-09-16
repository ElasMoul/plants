package com.plantpal.session.dto;

import lombok.Builder;
import lombok.Getter;

/** The server's answer to "am I still signed in, and for how long" (apiSketch's GET /auth/session). */
@Getter
@Builder
public class SessionStatusResponse {

  private boolean active;
  private Long secondsRemaining;
  private Long absoluteSecondsRemaining;
  private String revokedReason;
  private boolean enforcementActive;
}
