package com.plantpal.user.service;

import com.plantpal.user.entity.User;

/**
 * Proves a user owns their email address. Tokens are random, single-use, expire, and are stored
 * only as a hash. Verification is never a precondition for signing in or recovering a password.
 */
public interface EmailVerificationService {

  /** Issues a fresh link (retiring older ones) and queues the email. No rate limit. */
  void sendVerification(User user);

  /**
   * Sends a new link to the signed-in user. A no-op for an already verified user.
   *
   * @throws com.plantpal.shared.exception.RateLimitException when asked too often
   */
  void resend(Long userId);

  /**
   * Spends the token and marks the address verified.
   *
   * @throws com.plantpal.shared.exception.ValidationException the same way for an unknown, altered,
   *     reused or expired token, or one issued for an address the user no longer has
   */
  void verify(String token);

  /**
   * Fails unless the user's address is verified.
   *
   * @throws com.plantpal.shared.exception.EmailNotVerifiedException when it is not
   */
  void requireVerified(Long userId);
}
