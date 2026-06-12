package com.plantpal.user.service;

import com.plantpal.user.dto.AuthResponse;
import com.plantpal.user.dto.LoginRequest;
import com.plantpal.user.dto.RegisterRequest;

public interface UserService {

  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);
}
