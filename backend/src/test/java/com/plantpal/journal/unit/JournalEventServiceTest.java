package com.plantpal.journal.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.journal.dto.JournalEventRequest;
import com.plantpal.journal.dto.JournalEventResponse;
import com.plantpal.journal.entity.JournalEventType;
import com.plantpal.journal.entity.PlantJournalEvent;
import com.plantpal.journal.repository.PlantJournalEventRepository;
import com.plantpal.journal.service.impl.JournalEventServiceImpl;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalEventService - Unit Tests")
class JournalEventServiceTest {

  private static final Long USER = 1L;
  private static final Long OTHER_USER = 2L;
  private static final Long PLANT = 10L;
  private static final Long EVENT = 100L;
  private static final LocalDate DAY = LocalDate.of(2026, 5, 1);

  @Mock private PlantJournalEventRepository eventRepository;
  @Mock private PlantRepository plantRepository;
  @InjectMocks private JournalEventServiceImpl service;

  @Test
  @DisplayName("create stores the event for the owner, with and without a note")
  void createStoresEvent() {
    ownPlant();
    when(eventRepository.save(any(PlantJournalEvent.class)))
        .thenAnswer(inv -> withId(inv.getArgument(0)));

    JournalEventResponse withNote =
        service.createEvent(
            PLANT, new JournalEventRequest(JournalEventType.REPOTTING, DAY, " new pot "), USER);
    JournalEventResponse noNote =
        service.createEvent(
            PLANT, new JournalEventRequest(JournalEventType.PRUNING, DAY, "  "), USER);

    assertThat(withNote.getNote()).isEqualTo("new pot");
    assertThat(noNote.getNote()).isNull();
    ArgumentCaptor<PlantJournalEvent> saved = ArgumentCaptor.forClass(PlantJournalEvent.class);
    verify(eventRepository, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).allMatch(e -> USER.equals(e.getUserId()));
  }

  @Test
  @DisplayName("create on another user's plant is not found and saves nothing")
  void createOnForeignPlant() {
    when(plantRepository.findByIdAndUserIdAndStatus(PLANT, OTHER_USER, PlantStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.createEvent(
                    PLANT,
                    new JournalEventRequest(JournalEventType.FLOWERING, DAY, null),
                    OTHER_USER))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(eventRepository, never()).save(any());
  }

  @Test
  @DisplayName("update replaces type, date and note for the owner")
  void updateReplaces() {
    ownPlant();
    PlantJournalEvent existing = withId(event());
    when(eventRepository.findByIdAndPlantIdAndUserId(EVENT, PLANT, USER))
        .thenReturn(Optional.of(existing));
    when(eventRepository.save(existing)).thenReturn(existing);

    JournalEventResponse res =
        service.updateEvent(
            PLANT,
            EVENT,
            new JournalEventRequest(JournalEventType.FRUITING, DAY.plusDays(3), "ripe"),
            USER);

    assertThat(res.getType()).isEqualTo(JournalEventType.FRUITING);
    assertThat(res.getEventDate()).isEqualTo(DAY.plusDays(3));
    assertThat(res.getNote()).isEqualTo("ripe");
  }

  @Test
  @DisplayName("update and delete by a non-owner are not found and change nothing")
  void foreignUpdateAndDelete() {
    ownPlant(OTHER_USER);
    when(eventRepository.findByIdAndPlantIdAndUserId(EVENT, PLANT, OTHER_USER))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.updateEvent(
                    PLANT,
                    EVENT,
                    new JournalEventRequest(JournalEventType.PRUNING, DAY, null),
                    OTHER_USER))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.deleteEvent(PLANT, EVENT, OTHER_USER))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(eventRepository, never()).save(any());
    verify(eventRepository, never()).delete(any());
  }

  @Test
  @DisplayName("delete removes the owner's event")
  void deleteOwn() {
    ownPlant();
    PlantJournalEvent existing = withId(event());
    when(eventRepository.findByIdAndPlantIdAndUserId(EVENT, PLANT, USER))
        .thenReturn(Optional.of(existing));

    service.deleteEvent(PLANT, EVENT, USER);

    verify(eventRepository).delete(existing);
  }

  @Test
  @DisplayName("list maps the repository page and requires plant ownership")
  void list() {
    ownPlant();
    var pageable = PageRequest.of(0, 10);
    when(eventRepository.findAllByPlantIdAndUserId(PLANT, USER, pageable))
        .thenReturn(new PageImpl<>(List.of(withId(event()))));

    assertThat(service.listEvents(PLANT, USER, pageable).getContent()).hasSize(1);
  }

  private void ownPlant() {
    ownPlant(USER);
  }

  private void ownPlant(Long userId) {
    when(plantRepository.findByIdAndUserIdAndStatus(PLANT, userId, PlantStatus.ACTIVE))
        .thenReturn(Optional.of(Plant.builder().id(PLANT).userId(userId).build()));
  }

  private static PlantJournalEvent event() {
    return PlantJournalEvent.builder()
        .plantId(PLANT)
        .userId(USER)
        .type(JournalEventType.REPOTTING)
        .eventDate(DAY)
        .build();
  }

  private static PlantJournalEvent withId(PlantJournalEvent e) {
    e.setId(EVENT);
    return e;
  }
}
