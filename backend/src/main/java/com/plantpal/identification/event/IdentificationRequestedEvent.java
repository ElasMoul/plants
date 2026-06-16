package com.plantpal.identification.event;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentificationRequestedEvent {

  private Long identificationId;
  private Long userId;
  private String photoUrl;
  private String aiModelPreference;
  private List<String> organs;
  private Instant requestedAt;
}
