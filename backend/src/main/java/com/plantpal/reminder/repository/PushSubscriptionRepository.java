package com.plantpal.reminder.repository;

import com.plantpal.reminder.entity.PushSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

  List<PushSubscription> findByUserIdAndEnabledTrue(Long userId);
}
