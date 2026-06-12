package com.plantpal.testdata;

import com.plantpal.user.dto.LoginRequest;
import com.plantpal.user.dto.RegisterRequest;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;

public final class UserTestDataBuilder {

  private UserTestDataBuilder() {}

  public static UserBuilder aUser() {
    return new UserBuilder();
  }

  public static RegisterRequestBuilder aRegisterRequest() {
    return new RegisterRequestBuilder();
  }

  public static LoginRequestBuilder aLoginRequest() {
    return new LoginRequestBuilder();
  }

  public static final class UserBuilder {

    private Long id = 1L;
    private String email = "test@example.com";
    private String passwordHash = "$2a$12$hashedPasswordForTesting";
    private String firstName = "John";
    private String lastName = "Doe";
    private UserStatus status = UserStatus.ACTIVE;

    public UserBuilder withId(Long id) {
      this.id = id;
      return this;
    }

    public UserBuilder withEmail(String email) {
      this.email = email;
      return this;
    }

    public UserBuilder withPasswordHash(String passwordHash) {
      this.passwordHash = passwordHash;
      return this;
    }

    public UserBuilder withFirstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    public UserBuilder withStatus(UserStatus status) {
      this.status = status;
      return this;
    }

    public User build() {
      return User.builder()
          .id(id)
          .email(email)
          .passwordHash(passwordHash)
          .firstName(firstName)
          .lastName(lastName)
          .status(status)
          .build();
    }
  }

  public static final class RegisterRequestBuilder {

    private String email = "test@example.com";
    private String password = "password123";
    private String firstName = "John";
    private String lastName = "Doe";

    public RegisterRequestBuilder withEmail(String email) {
      this.email = email;
      return this;
    }

    public RegisterRequestBuilder withPassword(String password) {
      this.password = password;
      return this;
    }

    public RegisterRequest build() {
      RegisterRequest request = new RegisterRequest();
      request.setEmail(email);
      request.setPassword(password);
      request.setFirstName(firstName);
      request.setLastName(lastName);
      return request;
    }
  }

  public static final class LoginRequestBuilder {

    private String email = "test@example.com";
    private String password = "password123";

    public LoginRequestBuilder withEmail(String email) {
      this.email = email;
      return this;
    }

    public LoginRequestBuilder withPassword(String password) {
      this.password = password;
      return this;
    }

    public LoginRequest build() {
      LoginRequest request = new LoginRequest();
      request.setEmail(email);
      request.setPassword(password);
      return request;
    }
  }
}
