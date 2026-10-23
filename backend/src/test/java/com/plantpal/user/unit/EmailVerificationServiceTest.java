package com.plantpal.user.unit;

import static com.plantpal.testdata.UserTestDataBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.service.MailService;
import com.plantpal.shared.exception.EmailNotVerifiedException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.user.entity.EmailVerificationToken;
import com.plantpal.user.entity.User;
import com.plantpal.user.repository.EmailVerificationTokenRepository;
import com.plantpal.user.repository.UserRepository;
import com.plantpal.user.service.impl.EmailVerificationServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService — Unit Tests")
class EmailVerificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

  @Mock private UserRepository users;
  @Mock private EmailVerificationTokenRepository tokens;
  @Mock private MailService mailService;

  private EmailVerificationServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new EmailVerificationServiceImpl(
            users,
            tokens,
            mailService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            "https://app.example.test/");
  }

  @Nested
  @DisplayName("sendVerification()")
  class SendVerification {

    @Test
    @DisplayName("stores only a hash, expires in 24h and mails a link to the user's language")
    @SuppressWarnings("unchecked")
    void issuesHashedExpiringToken() {
      User user = aUser().withId(7L).withEmail("grower@example.test").build();
      user.setLanguage("fr");

      service.sendVerification(user);

      ArgumentCaptor<EmailVerificationToken> saved =
          ArgumentCaptor.forClass(EmailVerificationToken.class);
      verify(tokens).invalidateOpen(7L, NOW);
      verify(tokens).save(saved.capture());
      ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
      verify(mailService)
          .send(
              eq(EmailTemplate.ACCOUNT_VERIFICATION),
              eq("grower@example.test"),
              eq("fr"),
              vars.capture());

      String link = vars.getValue().get("link");
      String rawToken = link.substring(link.indexOf("token=") + "token=".length());
      assertThat(link).startsWith("https://app.example.test/login/verify-email?token=");
      assertThat(rawToken).hasSize(43); // 32 bytes, base64url, no padding
      assertThat(saved.getValue().getTokenHash())
          .hasSize(64)
          .isNotEqualTo(rawToken)
          .doesNotContain(rawToken);
      assertThat(saved.getValue().getEmail()).isEqualTo("grower@example.test");
      assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("two issues never produce the same token")
    @SuppressWarnings("unchecked")
    void tokensAreRandom() {
      User user = aUser().withId(7L).build();

      service.sendVerification(user);
      service.sendVerification(user);

      ArgumentCaptor<EmailVerificationToken> saved =
          ArgumentCaptor.forClass(EmailVerificationToken.class);
      verify(tokens, org.mockito.Mockito.times(2)).save(saved.capture());
      assertThat(saved.getAllValues().get(0).getTokenHash())
          .isNotEqualTo(saved.getAllValues().get(1).getTokenHash());
    }
  }

  @Nested
  @DisplayName("verify()")
  class Verify {

    @Test
    @DisplayName("marks the user verified when the token is spent and the address matches")
    void verifiesOnFirstUse() {
      User user = aUser().withId(7L).withEmail("grower@example.test").build();
      when(tokens.consume(anyString(), eq(NOW))).thenReturn(1);
      when(tokens.findByTokenHash(anyString()))
          .thenReturn(Optional.of(token("grower@example.test")));
      when(users.findById(7L)).thenReturn(Optional.of(user));

      service.verify("some-token");

      verify(users).markEmailVerified(7L, NOW);
    }

    @Test
    @DisplayName("refuses a reused, expired or unknown token with the generic error")
    void refusesWhenTokenNotSpent() {
      when(tokens.consume(anyString(), any())).thenReturn(0);

      assertThatThrownBy(() -> service.verify("reused-expired-or-altered"))
          .isInstanceOf(ValidationException.class)
          .hasMessage("This verification link is invalid or has expired");
      verify(users, never()).markEmailVerified(any(), any());
    }

    @Test
    @DisplayName("refuses a token issued for an address the user no longer has")
    void refusesChangedEmail() {
      User user = aUser().withId(7L).withEmail("new@example.test").build();
      when(tokens.consume(anyString(), any())).thenReturn(1);
      when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token("old@example.test")));
      when(users.findById(7L)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> service.verify("tok"))
          .isInstanceOf(ValidationException.class)
          .hasMessage("This verification link is invalid or has expired");
      verify(users, never()).markEmailVerified(any(), any());
    }

    @Test
    @DisplayName("gives a null token the same refusal")
    void refusesNullToken() {
      assertThatThrownBy(() -> service.verify(null)).isInstanceOf(ValidationException.class);
    }

    private EmailVerificationToken token(String email) {
      EmailVerificationToken t = new EmailVerificationToken();
      t.setUserId(7L);
      t.setEmail(email);
      return t;
    }
  }

  @Nested
  @DisplayName("resend()")
  class Resend {

    @Test
    @DisplayName("is a no-op that sends nothing for an already verified user")
    void noOpWhenVerified() {
      User user = aUser().withId(7L).build();
      user.setEmailVerifiedAt(NOW.minusSeconds(5));
      when(users.findById(7L)).thenReturn(Optional.of(user));

      service.resend(7L);

      verify(mailService, never()).send(any(), anyString(), anyString(), anyMap());
      verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("is refused within 60 seconds of the previous link, with the wait time")
    void cooldown() {
      User user = aUser().withId(7L).build();
      when(users.findById(7L)).thenReturn(Optional.of(user));
      when(tokens.findFirstByUserIdOrderByCreatedAtDesc(7L))
          .thenReturn(Optional.of(issuedAt(NOW.minusSeconds(20))));

      assertThatThrownBy(() -> service.resend(7L))
          .isInstanceOfSatisfying(
              RateLimitException.class,
              e -> assertThat(e.getRetryAfterSeconds()).isBetween(40L, 41L));
      verify(mailService, never()).send(any(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("is allowed once the 60 second cooldown has passed")
    void allowedAfterCooldown() {
      User user = aUser().withId(7L).build();
      when(users.findById(7L)).thenReturn(Optional.of(user));
      when(tokens.findFirstByUserIdOrderByCreatedAtDesc(7L))
          .thenReturn(Optional.of(issuedAt(NOW.minusSeconds(61))));
      when(tokens.countByUserIdAndCreatedAtAfter(eq(7L), any())).thenReturn(2L);

      service.resend(7L);

      verify(mailService)
          .send(eq(EmailTemplate.ACCOUNT_VERIFICATION), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("is refused after five links in the last hour")
    void hourlyLimit() {
      User user = aUser().withId(7L).build();
      when(users.findById(7L)).thenReturn(Optional.of(user));
      when(tokens.findFirstByUserIdOrderByCreatedAtDesc(7L))
          .thenReturn(Optional.of(issuedAt(NOW.minusSeconds(300))));
      when(tokens.countByUserIdAndCreatedAtAfter(7L, NOW.minus(Duration.ofHours(1))))
          .thenReturn(5L);

      assertThatThrownBy(() -> service.resend(7L)).isInstanceOf(RateLimitException.class);
      verify(mailService, never()).send(any(), anyString(), anyString(), anyMap());
    }

    private EmailVerificationToken issuedAt(Instant at) {
      EmailVerificationToken t = new EmailVerificationToken();
      t.setCreatedAt(at);
      return t;
    }
  }

  @Nested
  @DisplayName("requireVerified()")
  class RequireVerified {

    @Test
    @DisplayName("throws EmailNotVerifiedException for an unverified user")
    void blocksUnverified() {
      when(users.existsByIdAndEmailVerifiedAtIsNotNull(7L)).thenReturn(false);

      assertThatThrownBy(() -> service.requireVerified(7L))
          .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    @DisplayName("lets a verified user through")
    void allowsVerified() {
      when(users.existsByIdAndEmailVerifiedAtIsNotNull(7L)).thenReturn(true);

      service.requireVerified(7L);
    }
  }

  @Test
  @DisplayName("refuses a non-http base URL at construction")
  void rejectsBadBaseUrl() {
    assertThatThrownBy(
            () ->
                new EmailVerificationServiceImpl(
                    users, tokens, mailService, Clock.systemUTC(), "javascript:alert(1)"))
        .isInstanceOf(IllegalStateException.class);
  }
}
