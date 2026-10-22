package com.plantpal.journal.service;

import com.plantpal.journal.dto.JournalEventRequest;
import com.plantpal.journal.dto.JournalEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JournalEventService {

  Page<JournalEventResponse> listEvents(Long plantId, Long userId, Pageable pageable);

  JournalEventResponse createEvent(Long plantId, JournalEventRequest request, Long userId);

  JournalEventResponse updateEvent(
      Long plantId, Long eventId, JournalEventRequest request, Long userId);

  void deleteEvent(Long plantId, Long eventId, Long userId);
}
