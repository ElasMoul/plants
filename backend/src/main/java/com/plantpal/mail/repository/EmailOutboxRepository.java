package com.plantpal.mail.repository;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

  /**
   * Locks due rows for this instance only: rows another instance already holds are skipped, so two
   * dispatchers never send the same email.
   */
  @Query(
      value =
          "SELECT * FROM email_outbox WHERE status = 'PENDING' AND next_attempt_at <= :now "
              + "ORDER BY next_attempt_at, id LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<EmailOutbox> lockDue(@Param("now") Instant now, @Param("limit") int limit);

  List<EmailOutbox> findByTemplateAndRecipientAndStatus(
      EmailTemplate template, String recipient, EmailStatus status);

  Page<EmailOutbox> findByStatus(EmailStatus status, Pageable pageable);
}
