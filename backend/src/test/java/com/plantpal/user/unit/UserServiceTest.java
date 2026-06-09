package com.plantpal.user.unit;

import static com.plantpal.testdata.UserTestDataBuilder.aLoginRequest;
import static com.plantpal.testdata.UserTestDataBuilder.aRegisterRequest;
import static com.plantpal.testdata.UserTestDataBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.shared.exception.UnauthorizedException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.dto.AuthResponse;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import com.plantpal.user.service.impl.UserServiceImpl;
import java.util.Optional;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — Unit Tests")
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;

  @InjectMocks private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(userService, "jwtExpirationMs", 3_600_000L);
  }

  @Nested
  @DisplayName("register()")
  class Register {

    @Test
    @DisplayName("should register user and return token when email is new")
    void shouldRegisterSuccessfully() {
      // Given
      var request = aRegisterRequest().build();
      var savedUser = aUser().withId(42L).build();
      when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
      when(passwordEncoder.encode(request.getPassword())).thenReturn("$2a$12$hashed");
      when(userRepository.save(any(User.class))).thenReturn(savedUser);
      when(jwtUtil.generateToken(savedUser, savedUser.getId())).thenReturn("jwt-token");

      // When
      AuthResponse response = userService.register(request);

      // Then
      assertThat(response.getToken()).isEqualTo("jwt-token");
      assertThat(response.getEmail()).isEqualTo(savedUser.getEmail());
      assertThat(response.getUserId()).isEqualTo(42L);
      verify(userRepository).save(any(User.class));
      verify(passwordEncoder).encode(request.getPassword());
    }

    @Test
    @DisplayName("should throw ValidationException when email is already registered")
    void shouldThrowWhenEmailAlreadyExists() {
      // Given
      var request = aRegisterRequest().build();
      when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

      // When / Then
      assertThatThrownBy(() -> userService.register(request))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("Email already registered");

      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("login()")
  class Login {

    @Test
    @DisplayName("should return token when credentials are valid")
    void shouldLoginSuccessfully() {
      // Given
      var request = aLoginRequest().build();
      var user = aUser().build();
      when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
      when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);
      when(jwtUtil.generateToken(user, user.getId())).thenReturn("jwt-token");

      // When
      AuthResponse response = userService.login(request);

      // Then
      assertThat(response.getToken()).isEqualTo("jwt-token");
      assertThat(response.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("should throw UnauthorizedException when password is wrong")
    void shouldThrowWhenPasswordIsWrong() {
      // Given
      var request = aLoginRequest().build();
      var user = aUser().build();
      when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
      when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
          .thenReturn(false);

      // When / Then
      assertThatThrownBy(() -> userService.login(request))
          .isInstanceOf(UnauthorizedException.class)
          .hasMessageContaining("Invalid credentials");

      verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("should throw UnauthorizedException when user does not exist")
    void shouldThrowWhenUserNotFound() {
      // Given
      var request = aLoginRequest().withEmail("ghost@example.com").build();
      when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

      // When / Then
      assertThatThrownBy(() -> userService.login(request))
          .isInstanceOf(UnauthorizedException.class)
          .hasMessageContaining("Invalid credentials");

      verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("should throw UnauthorizedException when account is not active")
    void shouldThrowWhenAccountIsInactive() {
      // Given
      var request = aLoginRequest().build();
      var inactiveUser = aUser().withStatus(UserStatus.INACTIVE).build();
      when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(inactiveUser));
      when(passwordEncoder.matches(request.getPassword(), inactiveUser.getPasswordHash()))
          .thenReturn(true);

      // When / Then
      assertThatThrownBy(() -> userService.login(request))
          .isInstanceOf(UnauthorizedException.class)
          .hasMessageContaining("Account is not active");
    }
  }

  @Nested
  @DisplayName("loadUserByUsername()")
  class LoadUserByUsername {

    @Test
    @DisplayName("should return UserDetails when the email exists")
    void shouldReturnUserDetailsWhenFound() {
      // Given
      var user = aUser().withId(1L).build();
      when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

      // When
      var result = userService.loadUserByUsername(user.getEmail());

      // Then
      assertThat(result.getUsername()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("should throw UsernameNotFoundException when email does not exist")
    void shouldThrowWhenEmailNotFound() {
      // Given
      String email = "ghost@example.com";
      when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

      // When / Then
      assertThatThrownBy(() -> userService.loadUserByUsername(email))
          .isInstanceOf(UsernameNotFoundException.class);
    }
  }
}
