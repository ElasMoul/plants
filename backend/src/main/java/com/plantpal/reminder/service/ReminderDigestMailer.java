package com.plantpal.reminder.service;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.service.MailService;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.user.entity.User;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Builds the daily reminder digest for one user and queues it through the mail outbox. */
@Component
public class ReminderDigestMailer {

  private static final Set<String> LABEL_LANGUAGES = Set.of("en", "fr");
  private static final int MAX_LISTED = 25;
  private static final String FALLBACK_LANGUAGE = "en";
  private static final String UNSUBSCRIBE_PATH = "/api/v1/email/unsubscribe?token=";
  private static final Map<String, Properties> LABELS = new ConcurrentHashMap<>();

  private final MailService mailService;
  private final PlantRepository plantRepository;
  private final String publicBaseUrl;

  public ReminderDigestMailer(
      MailService mailService,
      PlantRepository plantRepository,
      @Value("${app.public-base-url:http://localhost:4200}") String publicBaseUrl) {
    this.mailService = mailService;
    this.plantRepository = plantRepository;
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  /** Queues the digest; joins the caller's transaction like every other send. */
  public void enqueue(User user, List<Reminder> due, String unsubscribeToken) {
    String language = user.getLanguage();
    Map<String, String> variables = new HashMap<>();
    variables.put("name", user.getFirstName());
    variables.put("link", publicBaseUrl + "/");
    variables.put("reminders", renderReminders(due, language));
    variables.put("unsubscribeUrl", unsubscribeUrl(unsubscribeToken));
    mailService.send(EmailTemplate.REMINDER_DIGEST, user.getEmail(), language, variables);
  }

  String unsubscribeUrl(String token) {
    return publicBaseUrl + UNSUBSCRIBE_PATH + token;
  }

  private String renderReminders(List<Reminder> due, String language) {
    Properties labels = labels(language);
    Map<Long, String> nicknames =
        plantRepository
            .findAllById(due.stream().map(Reminder::getPlantId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Plant::getId, Plant::getNickname));
    return due.stream()
        .limit(MAX_LISTED)
        .map(
            r ->
                "- "
                    + nicknames.getOrDefault(r.getPlantId(), "?")
                    + ": "
                    + labels.getProperty(r.getCareType().name(), r.getCareType().name()))
        .collect(Collectors.joining("\n"));
  }

  private static Properties labels(String language) {
    String code = language == null || language.length() < 2 ? "" : language.substring(0, 2);
    String lang = code.toLowerCase(Locale.ROOT);
    String resolved = LABEL_LANGUAGES.contains(lang) ? lang : FALLBACK_LANGUAGE;
    return LABELS.computeIfAbsent(resolved, ReminderDigestMailer::loadLabels);
  }

  private static Properties loadLabels(String lang) {
    Properties props = new Properties();
    try (Reader in =
        new InputStreamReader(
            new ClassPathResource("mail/care-type." + lang + ".properties").getInputStream(),
            StandardCharsets.UTF_8)) {
      props.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Care type labels missing for " + lang, e);
    }
    return props;
  }
}
