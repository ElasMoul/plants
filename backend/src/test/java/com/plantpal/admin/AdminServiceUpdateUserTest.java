package com.plantpal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.admin.dto.AdminDtos.UserUpdate;
import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.admin.service.impl.AdminServiceImpl;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import com.plantpal.user.service.UsageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceUpdateUserTest {
  private static final String EMAIL = "secret.person@example.test";
  private static final String HASH = "$2a$10$verySecretHashValue";

  @Mock private UserRepository users;
  @Mock private AdminRepository audit;
  @Mock private UsageService usage;
  private AdminServiceImpl service;
  private User admin;
  private User target;

  @BeforeEach
  void setUp() {
    service = new AdminServiceImpl(users, audit, usage);
    admin = user(1L, UserRole.ADMIN);
    target = user(2L, UserRole.USER);
    when(users.findById(1L)).thenReturn(Optional.of(admin));
  }

  @Test
  void recordsActorTargetAndOldToNewValues() {
    when(users.findForAdminUpdate(2L)).thenReturn(Optional.of(target));
    target.setMaxPlants(10);
    target.setDailyScanLimit(5);
    target.setDailyAiLimit(20);

    service.updateUser(
        2L,
        new UserUpdate(
            "Ada", "Lovelace", UserStatus.SUSPENDED, UserRole.ADMIN, true, 3L, 20, 5, 30),
        1L);

    var action = ArgumentCaptor.forClass(String.class);
    verify(audit).record(eq(1L), action.capture(), eq("user:2"));
    assertThat(action.getValue())
        .startsWith("User updated: role USER → ADMIN, status ACTIVE → SUSPENDED")
        .contains("businessTier false → true")
        .contains("plants 10 → 20", "scans/day 5 → 5", "AI/day 20 → 30")
        .hasSizeLessThanOrEqualTo(255)
        .doesNotContain(EMAIL, HASH, "Ada", "Lovelace", "Grace", "Hopper");
  }

  @Test
  void staleVersionRecordsNothing() {
    when(users.findForAdminUpdate(2L)).thenReturn(Optional.of(target));
    assertThatThrownBy(
            () ->
                service.updateUser(
                    2L,
                    new UserUpdate(
                        "A", "B", UserStatus.ACTIVE, UserRole.USER, false, 99L, null, null, null),
                    1L))
        .isInstanceOf(ValidationException.class);
    verify(audit, never()).record(any(), any(), any());
  }

  @Test
  void selfDemotionRecordsNothing() {
    when(users.findForAdminUpdate(1L)).thenReturn(Optional.of(admin));
    assertThatThrownBy(
            () ->
                service.updateUser(
                    1L,
                    new UserUpdate(
                        "A", "B", UserStatus.ACTIVE, UserRole.USER, false, 3L, null, null, null),
                    1L))
        .isInstanceOf(ValidationException.class);
    verify(audit, never()).record(any(), any(), any());
  }

  private static User user(Long id, UserRole role) {
    User u =
        User.builder()
            .firstName("Grace")
            .lastName("Hopper")
            .email(EMAIL)
            .passwordHash(HASH)
            .status(UserStatus.ACTIVE)
            .role(role)
            .build();
    u.setId(id);
    u.setVersion(3L);
    return u;
  }
}
