package com.plantpal.user.service.impl;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.service.MailService;
import com.plantpal.shared.exception.EmailNotVerifiedException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.user.entity.EmailVerificationToken;
import com.plantpal.user.entity.User;
import com.plantpal.user.repository.EmailVerificationTokenRepository;
import com.plantpal.user.repository.UserRepository;
import com.plantpal.user.service.EmailVerificationService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationServiceImpl.class);

  static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
  static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
  static final Duration RESEND_WINDOW = Duration.ofHours(1);
  static final int MAX_PER_WINDOW = 5;

  private static final int TOKEN_BYTES = 32;
  private static final String INVALID_LINK = "This verification link is invalid or has expired";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final EmailVerificationTokenRepository tokens;
  private final MailService mailService;
  private final Clock clock;
  private final String publicBaseUrl;

  public EmailVerificationServiceImpl(
      UserRepository users,
      EmailVerificationTokenRepository tokens,
      MailService mailService,
      Clock clock,
      @Value("${app.mail.public-base-url:http://localhost:4200}") String publicBaseUrl) {
    if (!publicBaseUrl.startsWith("http://") && !publicBaseUrl.startsWith("https://")) {
      throw new IllegalStateException("app.mail.public-base-url must be an http(s) URL");
    }
    this.users = users;
    this.tokens = tokens;
    this.mailService = mailService;
    this.clock = clock;
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  @Override
  @Transactional
  public void sendVerification(User user) {
    Instant now = clock.instant();
    tokens.invalidateOpen(user.getId(), now);

    byte[] raw = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

    EmailVerificationToken row = new EmailVerificationToken();
    row.setUserId(user.getId());
    row.setTokenHash(hash(token));
    row.setEmail(user.getEmail());
    row.setCreatedAt(now);
    row.setExpiresAt(now.plus(TOKEN_LIFETIME));
    tokens.save(row);

    String link =
        publicBaseUrl
            + "/login/verify-email?token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
    mailService.send(
        EmailTemplate.ACCOUNT_VERIFICATION,
        user.getEmail(),
        user.getLanguage(),
        Map.of("name", user.getFirstName(), "link", link));
    log.info("Verification email queued: userId={}", user.getId());
  }

  @Override
  @Transactional
  public void resend(Long userId) {
    User user =
        users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    if (user.isEmailVerified()) {
      return;
    }
    enforceResendLimits(userId, clock.instant());
    sendVerification(user);
  }

  @Override
  @Transactional
  public void verify(String token) {
    Instant now = clock.instant();
    String hash = hash(token == null ? "" : token);
    if (tokens.consume(hash, now) != 1) {
      throw new ValidationException(INVALID_LINK);
    }
    EmailVerificationToken row = tokens.findByTokenHash(hash).orElseThrow();
    User user =
        users.findById(row.getUserId()).orElseThrow(() -> new ValidationException(INVALID_LINK));
    if (!row.getEmail().equals(user.getEmail())) {
      // Rolls back the spend: a link for an address the user no longer has proves nothing.
      throw new ValidationException(INVALID_LINK);
    }
    users.markEmailVerified(user.getId(), now);
    log.info("Email verified: userId={}", user.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public void requireVerified(Long userId) {
    if (!users.existsByIdAndEmailVerifiedAtIsNotNull(userId)) {
      throw new EmailNotVerifiedException();
    }
  }

  private void enforceResendLimits(Long userId, Instant now) {
    tokens
        .findFirstByUserIdOrderByCreatedAtDesc(userId)
        .ifPresent(
            last -> {
              Instant next = last.getCreatedAt().plus(RESEND_COOLDOWN);
              if (next.isAfter(now)) {
                throw new RateLimitException(
                    "Please wait before requesting another verification email",
                    Duration.between(now, next).toSeconds() + 1);
              }
            });
    if (tokens.countByUserIdAndCreatedAtAfter(userId, now.minus(RESEND_WINDOW)) >= MAX_PER_WINDOW) {
      throw new RateLimitException(
          "Too many verification emails requested; try again later", RESEND_WINDOW.toSeconds());
    }
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
