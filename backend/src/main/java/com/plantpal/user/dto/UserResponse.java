package com.plantpal.user.dto;

import com.plantpal.user.entity.UserStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

  private Long id;

  private String email;

  private String firstName;

  private String lastName;

  private UserStatus status;

  private Instant createdAt;
}
