package com.plantpal.user.service;

import com.plantpal.user.dto.AuthResponse;
import com.plantpal.user.dto.LoginRequest;
import com.plantpal.user.dto.RegisterRequest;
import com.plantpal.user.dto.UserPreferencesRequest;
import com.plantpal.user.dto.UserPreferencesResponse;

public interface UserService {

  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  UserPreferencesResponse getPreferences(Long userId);

  UserPreferencesResponse updatePreferences(Long userId, UserPreferencesRequest request);
}
