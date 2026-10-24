package com.plantpal.reminder.repository;

import com.plantpal.reminder.entity.ReminderPreferences;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderPreferencesRepository extends JpaRepository<ReminderPreferences, Long> {

  Optional<ReminderPreferences> findByUnsubscribeToken(String unsubscribeToken);
}
