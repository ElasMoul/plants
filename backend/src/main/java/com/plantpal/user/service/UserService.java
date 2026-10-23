package com.plantpal.user.service;

import com.plantpal.user.dto.AuthResponse;
import com.plantpal.user.dto.ChangeEmailRequest;
import com.plantpal.user.dto.ChangePasswordRequest;
import com.plantpal.user.dto.LoginRequest;
import com.plantpal.user.dto.RegisterRequest;
import com.plantpal.user.dto.UserPreferencesRequest;
import com.plantpal.user.dto.UserPreferencesResponse;

public interface UserService {

  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  UserPreferencesResponse getPreferences(Long userId);

  /**
   * Changes the caller's password after checking the current one, then revokes the user's other
   * sessions (not {@code currentJti}).
   */
  void changePassword(Long userId, String currentJti, ChangePasswordRequest request);

  /** Starts an email change after checking the current password; see EmailVerificationService. */
  void requestEmailChange(Long userId, ChangeEmailRequest request);

  UserPreferencesResponse updatePreferences(Long userId, UserPreferencesRequest request);
}
