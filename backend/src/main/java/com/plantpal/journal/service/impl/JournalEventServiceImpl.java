package com.plantpal.journal.service.impl;

import com.plantpal.journal.dto.JournalEventRequest;
import com.plantpal.journal.dto.JournalEventResponse;
import com.plantpal.journal.entity.PlantJournalEvent;
import com.plantpal.journal.repository.PlantJournalEventRepository;
import com.plantpal.journal.service.JournalEventService;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.util.HtmlSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalEventServiceImpl implements JournalEventService {

  private static final Logger log = LoggerFactory.getLogger(JournalEventServiceImpl.class);

  private static final String PLANT_NOT_FOUND = "Plant not found";
  private static final String EVENT_NOT_FOUND = "Journal event not found";

  private final PlantJournalEventRepository eventRepository;
  private final PlantRepository plantRepository;

  public JournalEventServiceImpl(
      PlantJournalEventRepository eventRepository, PlantRepository plantRepository) {
    this.eventRepository = eventRepository;
    this.plantRepository = plantRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<JournalEventResponse> listEvents(Long plantId, Long userId, Pageable pageable) {
    requireOwnedPlant(plantId, userId);
    return eventRepository
        .findAllByPlantIdAndUserId(plantId, userId, pageable)
        .map(this::toResponse);
  }

  @Override
  @Transactional
  public JournalEventResponse createEvent(Long plantId, JournalEventRequest request, Long userId) {
    requireOwnedPlant(plantId, userId);
    PlantJournalEvent event =
        PlantJournalEvent.builder()
            .plantId(plantId)
            .userId(userId)
            .type(request.getType())
            .eventDate(request.getEventDate())
            .note(cleanNote(request.getNote()))
            .build();
    event = eventRepository.save(event);
    log.info("Journal event created: id={}, plantId={}, userId={}", event.getId(), plantId, userId);
    return toResponse(event);
  }

  @Override
  @Transactional
  public JournalEventResponse updateEvent(
      Long plantId, Long eventId, JournalEventRequest request, Long userId) {
    PlantJournalEvent event = findOwnedEvent(plantId, eventId, userId);
    event.setType(request.getType());
    event.setEventDate(request.getEventDate());
    event.setNote(cleanNote(request.getNote()));
    return toResponse(eventRepository.save(event));
  }

  @Override
  @Transactional
  public void deleteEvent(Long plantId, Long eventId, Long userId) {
    PlantJournalEvent event = findOwnedEvent(plantId, eventId, userId);
    eventRepository.delete(event);
    log.info("Journal event deleted: id={}, plantId={}, userId={}", eventId, plantId, userId);
  }

  private void requireOwnedPlant(Long plantId, Long userId) {
    plantRepository
        .findByIdAndUserIdAndStatus(plantId, userId, PlantStatus.ACTIVE)
        .orElseThrow(() -> new ResourceNotFoundException(PLANT_NOT_FOUND));
  }

  private PlantJournalEvent findOwnedEvent(Long plantId, Long eventId, Long userId) {
    requireOwnedPlant(plantId, userId);
    return eventRepository
        .findByIdAndPlantIdAndUserId(eventId, plantId, userId)
        .orElseThrow(() -> new ResourceNotFoundException(EVENT_NOT_FOUND));
  }

  private String cleanNote(String note) {
    if (note == null || note.isBlank()) {
      return null;
    }
    return HtmlSanitizer.sanitize(note.trim());
  }

  private JournalEventResponse toResponse(PlantJournalEvent event) {
    return JournalEventResponse.builder()
        .id(event.getId())
        .plantId(event.getPlantId())
        .type(event.getType())
        .eventDate(event.getEventDate())
        .note(event.getNote())
        .build();
  }
}
