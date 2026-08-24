package com.plantpal.user.entity;

import com.plantpal.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends AuditableEntity implements UserDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private UserStatus status;

  // Deprecated — superseded by visionModelPreference/reasoningModelPreference below.
  // Kept (not dropped) so existing callers aren't broken in this phase.
  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "ai_model_preference", nullable = false, length = 50)
  private AiModelPreference aiModelPreference = AiModelPreference.DEEPSEEK;

  // Defaults switched from GITHUB_GPT4O / DEEPSEEK_R1 after GitHub Models' upstream
  // retirement (2026-08, migration 033) — those routes no longer resolve.
  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "vision_model_preference", nullable = false, length = 30)
  private VisionModelPreference visionModelPreference = VisionModelPreference.ANTHROPIC_CLAUDE;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "reasoning_model_preference", nullable = false, length = 30)
  private ReasoningModelPreference reasoningModelPreference =
      ReasoningModelPreference.ANTHROPIC_CLAUDE;

  @Builder.Default
  @Column(name = "plantnet_project", length = 50)
  private String plantnetProject = "all";

  @Builder.Default
  @Column(name = "plantnet_lang", length = 10)
  private String plantnetLang = "en";

  // Self-declared business/professional tier (D027, PP-086). No server-side enforcement — the
  // N=10 plants upgrade prompt threshold is a frontend-only presentational check.
  @Builder.Default
  @Column(name = "is_business_tier", nullable = false)
  private boolean businessTier = false;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return status != UserStatus.SUSPENDED;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return status == UserStatus.ACTIVE;
  }
}
