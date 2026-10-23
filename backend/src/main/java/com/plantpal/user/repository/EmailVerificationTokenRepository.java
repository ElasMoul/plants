package com.plantpal.user.repository;

import com.plantpal.user.entity.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, Long> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  /**
   * Spends a token in one conditional statement, so of any number of concurrent attempts exactly
   * one sees a count of 1.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE EmailVerificationToken t SET t.usedAt = :now WHERE t.tokenHash = :hash "
          + "AND t.usedAt IS NULL AND t.expiresAt > :now")
  int consume(@Param("hash") String hash, @Param("now") Instant now);

  /** Retires every link still open for the user, so only the newest one works. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE EmailVerificationToken t SET t.usedAt = :now "
          + "WHERE t.userId = :userId AND t.usedAt IS NULL")
  int invalidateOpen(@Param("userId") Long userId, @Param("now") Instant now);

  long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

  Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
