package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.service.MailService;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.service.ReminderDigestMailer;
import com.plantpal.user.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderDigestMailer - Unit Tests")
class ReminderDigestMailerTest {

  @Mock private MailService mailService;
  @Mock private PlantRepository plantRepository;

  @Test
  @DisplayName("queues the digest with localized care labels, app link and unsubscribe URL")
  @SuppressWarnings("unchecked")
  void enqueuesDigest() {
    ReminderDigestMailer mailer =
        new ReminderDigestMailer(mailService, plantRepository, "https://www.planotell.com/");
    User user = User.builder().email("sara@example.com").firstName("Sara").language("fr").build();
    Plant plant = Plant.builder().userId(1L).nickname("Monstera").build();
    plant.setId(5L);
    when(plantRepository.findAllById(any())).thenReturn(List.of(plant));
    Reminder due = Reminder.builder().plantId(5L).userId(1L).careType(CareType.WATERING).build();

    mailer.enqueue(user, List.of(due), "tok123");

    ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
    verify(mailService)
        .send(eq(EmailTemplate.REMINDER_DIGEST), eq("sara@example.com"), eq("fr"), vars.capture());
    assertThat(vars.getValue())
        .containsEntry("name", "Sara")
        .containsEntry("link", "https://www.planotell.com/")
        .containsEntry("reminders", "- Monstera: Arrosage")
        .containsEntry(
            "unsubscribeUrl", "https://www.planotell.com/api/v1/email/unsubscribe?token=tok123");
  }
}
