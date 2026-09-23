package com.plantpal.user.repository;

import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @Query(
      "SELECT u FROM User u WHERE (:status IS NULL OR u.status = :status) AND "
          + "(LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :query, '%')))")
  Page<User> searchForAdmin(
      @Param("query") String query, @Param("status") UserStatus status, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM User u WHERE u.id = :id")
  Optional<User> findForAdminUpdate(@Param("id") Long id);
}
