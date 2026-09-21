package com.plantpal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plantpal.admin.config.AdminBootstrap;
import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {
  @Mock private UserRepository users;
  @Mock private AdminRepository audit;
  @InjectMocks private AdminBootstrap bootstrap;

  @Test
  void defaultConfigurationNeverGrantsAccess() {
    ReflectionTestUtils.setField(bootstrap, "email", "");
    bootstrap.run(new DefaultApplicationArguments());
    verifyNoInteractions(users, audit);
  }

  @Test
  void explicitlyConfiguredActiveAccountIsPromotedAndAuditedOnce() {
    var user = configuredUser(UserStatus.ACTIVE);
    bootstrap.run(new DefaultApplicationArguments());
    assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    verify(users).save(user);
    verify(audit)
        .record(
            user.getId(), "Initial administrator provisioned by server configuration", "user:9");
    bootstrap.run(new DefaultApplicationArguments());
    verify(users).save(user);
  }

  @Test
  void inactiveAccountsCannotBeBootstrapped() {
    configuredUser(UserStatus.SUSPENDED);
    assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active account");
    verify(users, never()).save(any());
  }

  @Test
  void unknownEmailDoesNotCreateAnAccount() {
    ReflectionTestUtils.setField(bootstrap, "email", "missing@example.test");
    when(users.findByEmail(anyString())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("existing account");
    verify(users, never()).save(any());
  }

  private User configuredUser(UserStatus status) {
    var user = User.builder().id(9L).email("owner@example.test").status(status).build();
    ReflectionTestUtils.setField(bootstrap, "email", user.getEmail());
    when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    return user;
  }
}
