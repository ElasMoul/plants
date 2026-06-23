package com.plantpal.user.service.impl;

import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.UnauthorizedException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.shared.util.JwtUtil;
import com.plantpal.user.dto.AuthResponse;
import com.plantpal.user.dto.LoginRequest;
import com.plantpal.user.dto.RegisterRequest;
import com.plantpal.user.dto.UserPreferencesRequest;
import com.plantpal.user.dto.UserPreferencesResponse;
import com.plantpal.user.entity.ReasoningModelPreference;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.entity.VisionModelPreference;
import com.plantpal.user.repository.UserRepository;
import com.plantpal.user.service.UserService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

  private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final AnthropicClient anthropicClient;

  @Value("${app.jwt.expiration-ms}")
  private long jwtExpirationMs;

  public UserServiceImpl(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil,
      AnthropicClient anthropicClient) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.anthropicClient = anthropicClient;
  }

  @Override
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new ValidationException("Email already registered");
    }

    User user =
        User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .status(UserStatus.ACTIVE)
            .build();

    user = userRepository.save(user);
    log.info("New user registered: id={}, email={}", user.getId(), user.getEmail());

    String token = jwtUtil.generateToken(user, user.getId());
    return buildAuthResponse(user, token);
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      log.warn("Failed login attempt for email={}", request.getEmail());
      throw new UnauthorizedException("Invalid credentials");
    }

    if (!user.isEnabled()) {
      throw new UnauthorizedException("Account is not active");
    }

    log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());

    String token = jwtUtil.generateToken(user, user.getId());
    return buildAuthResponse(user, token);
  }

  @Override
  @Transactional(readOnly = true)
  public UserPreferencesResponse getPreferences(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    return toPreferencesResponse(user);
  }

  @Override
  @Transactional
  public UserPreferencesResponse updatePreferences(Long userId, UserPreferencesRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    if (request.getAiModelPreference() != null) {
      user.setAiModelPreference(request.getAiModelPreference());
    }
    if (request.getVisionModelPreference() != null) {
      user.setVisionModelPreference(request.getVisionModelPreference());
    }
    if (request.getReasoningModelPreference() != null) {
      user.setReasoningModelPreference(request.getReasoningModelPreference());
    }
    userRepository.save(user);
    log.info(
        "User preference updated: userId={}, aiModelPreference={}, visionModelPreference={},"
            + " reasoningModelPreference={}",
        userId,
        user.getAiModelPreference(),
        user.getVisionModelPreference(),
        user.getReasoningModelPreference());
    return toPreferencesResponse(user);
  }

  private UserPreferencesResponse toPreferencesResponse(User user) {
    return UserPreferencesResponse.builder()
        .aiModelPreference(user.getAiModelPreference())
        .visionModelPreference(user.getVisionModelPreference())
        .reasoningModelPreference(user.getReasoningModelPreference())
        .visionModelAvailability(visionModelAvailability())
        .reasoningModelAvailability(reasoningModelAvailability())
        .build();
  }

  /**
   * Every option here other than ANTHROPIC_CLAUDE has its required config (github.token,
   * app.plantnet.api-key) enforced at application startup, so it's always available once the app
   * is running — only Claude's API key is genuinely optional (see {@link AnthropicClient}).
   */
  private Map<String, Boolean> visionModelAvailability() {
    Map<String, Boolean> availability = new LinkedHashMap<>();
    for (VisionModelPreference option : VisionModelPreference.values()) {
      availability.put(
          option.name(),
          option == VisionModelPreference.ANTHROPIC_CLAUDE ? anthropicClient.isAvailable() : true);
    }
    return availability;
  }

  private Map<String, Boolean> reasoningModelAvailability() {
    Map<String, Boolean> availability = new LinkedHashMap<>();
    for (ReasoningModelPreference option : ReasoningModelPreference.values()) {
      availability.put(
          option.name(),
          option == ReasoningModelPreference.ANTHROPIC_CLAUDE ? anthropicClient.isAvailable() : true);
    }
    return availability;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
  }

  private AuthResponse buildAuthResponse(User user, String token) {
    return AuthResponse.builder()
        .token(token)
        .expiresIn(jwtExpirationMs)
        .userId(user.getId())
        .email(user.getEmail())
        .firstName(user.getFirstName())
        .build();
  }
}
