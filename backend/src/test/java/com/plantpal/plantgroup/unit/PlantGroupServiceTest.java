package com.plantpal.plantgroup.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapper;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plantgroup.dto.GroupCareAction;
import com.plantpal.plantgroup.dto.GroupCareRequest;
import com.plantpal.plantgroup.dto.GroupResponse;
import com.plantpal.plantgroup.entity.PlantGroup;
import com.plantpal.plantgroup.repository.PlantGroupRepository;
import com.plantpal.plantgroup.service.impl.PlantGroupServiceImpl;
import com.plantpal.reminder.dto.CareLogResponse;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlantGroupService - Unit Tests")
class PlantGroupServiceTest {

  private static final Long USER = 1L;
  private static final Long OTHER_USER = 2L;
  private static final Long GROUP = 10L;

  @Mock private PlantGroupRepository groupRepository;
  @Mock private PlantRepository plantRepository;
  @Mock private PlantMapper plantMapper;
  @Mock private CareLogRepository careLogRepository;
  @Mock private ReminderRepository reminderRepository;
  @Mock private ReminderService reminderService;
  @InjectMocks private PlantGroupServiceImpl service;

  @Nested
  @DisplayName("create / rename / delete")
  class Crud {

    @Test
    @DisplayName("create sanitises and trims the name")
    void createSanitises() {
      when(groupRepository.save(any(PlantGroup.class))).thenAnswer(i -> i.getArgument(0));

      GroupResponse response = service.createGroup("  <b>Green</b>house ", USER);

      assertThat(response.getName()).doesNotContain("<").startsWith("Green");
      assertThat(response.getPlantCount()).isZero();
    }

    @Test
    @DisplayName("create rejects a name that is blank after sanitising")
    void createRejectsBlank() {
      assertThatThrownBy(() -> service.createGroup("   ", USER))
          .isInstanceOf(ValidationException.class);
      verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("rename updates an owned group")
    void renameOwned() {
      PlantGroup group = group(GROUP, USER, "Old");
      when(groupRepository.findByIdAndUserId(GROUP, USER)).thenReturn(Optional.of(group));
      when(groupRepository.save(group)).thenReturn(group);

      GroupResponse response = service.renameGroup(GROUP, "New", USER);

      assertThat(response.getName()).isEqualTo("New");
    }

    @Test
    @DisplayName("rename and delete of another user's group are 404 and change nothing")
    void foreignGroupIs404() {
      when(groupRepository.findByIdAndUserId(GROUP, OTHER_USER)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.renameGroup(GROUP, "X", OTHER_USER))
          .isInstanceOf(ResourceNotFoundException.class);
      assertThatThrownBy(() -> service.deleteGroup(GROUP, OTHER_USER))
          .isInstanceOf(ResourceNotFoundException.class);
      verify(groupRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete removes only the group entity")
    void deleteOwned() {
      PlantGroup group = group(GROUP, USER, "Bed");
      when(groupRepository.findByIdAndUserId(GROUP, USER)).thenReturn(Optional.of(group));

      service.deleteGroup(GROUP, USER);

      verify(groupRepository).delete(group);
      verify(plantRepository, never()).delete(any());
    }

    @Test
    @DisplayName("list returns the caller's groups with active plant counts")
    void listWithCounts() {
      PlantGroup group = group(GROUP, USER, "Bed");
      when(groupRepository.findAllByUserId(eq(USER), any()))
          .thenReturn(new PageImpl<>(List.of(group)));
      when(plantRepository.countByUserIdAndGroupIdAndStatus(USER, GROUP, PlantStatus.ACTIVE))
          .thenReturn(3L);

      var page = service.getUserGroups(USER, PageRequest.of(0, 100));

      assertThat(page.getContent())
          .singleElement()
          .extracting(GroupResponse::getPlantCount)
          .isEqualTo(3L);
    }
  }

  @Nested
  @DisplayName("assignPlant()")
  class Assign {

    @Test
    @DisplayName("assigns an owned plant to an owned group")
    void assigns() {
      Plant plant = plant(1L);
      when(plantRepository.findByIdAndUserIdAndStatus(1L, USER, PlantStatus.ACTIVE))
          .thenReturn(Optional.of(plant));
      when(groupRepository.findByIdAndUserId(GROUP, USER))
          .thenReturn(Optional.of(group(GROUP, USER, "Bed")));
      when(plantRepository.save(plant)).thenReturn(plant);
      when(plantMapper.toResponse(plant)).thenReturn(PlantResponse.builder().id(1L).build());

      service.assignPlant(1L, GROUP, USER);

      assertThat(plant.getGroupId()).isEqualTo(GROUP);
    }

    @Test
    @DisplayName("a null group id clears the grouping")
    void clears() {
      Plant plant = plant(1L);
      plant.setGroupId(GROUP);
      when(plantRepository.findByIdAndUserIdAndStatus(1L, USER, PlantStatus.ACTIVE))
          .thenReturn(Optional.of(plant));
      when(plantRepository.save(plant)).thenReturn(plant);
      when(plantMapper.toResponse(plant)).thenReturn(PlantResponse.builder().id(1L).build());

      service.assignPlant(1L, null, USER);

      assertThat(plant.getGroupId()).isNull();
    }

    @Test
    @DisplayName("another user's group is 404 and the plant is untouched")
    void foreignGroup() {
      Plant plant = plant(1L);
      when(plantRepository.findByIdAndUserIdAndStatus(1L, USER, PlantStatus.ACTIVE))
          .thenReturn(Optional.of(plant));
      when(groupRepository.findByIdAndUserId(GROUP, USER)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.assignPlant(1L, GROUP, USER))
          .isInstanceOf(ResourceNotFoundException.class);
      assertThat(plant.getGroupId()).isNull();
      verify(plantRepository, never()).save(any());
    }

    @Test
    @DisplayName("another user's plant is 404")
    void foreignPlant() {
      when(plantRepository.findByIdAndUserIdAndStatus(1L, OTHER_USER, PlantStatus.ACTIVE))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.assignPlant(1L, GROUP, OTHER_USER))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("logGroupCare()")
  class GroupCare {

    @Test
    @DisplayName("WATERED writes one WATERING log per active plant and reschedules the reminder")
    void watered() {
      stubGroup();
      Plant a = plant(1L);
      Plant b = plant(2L);
      when(plantRepository.findAllByUserIdAndGroupIdAndStatus(USER, GROUP, PlantStatus.ACTIVE))
          .thenReturn(List.of(a, b));
      when(careLogRepository.save(any(CareLog.class))).thenAnswer(i -> i.getArgument(0));
      Reminder watering = reminder(CareType.WATERING, null);
      Reminder feeding = reminder(CareType.FERTILIZING, null);
      Reminder treatmentStep = reminder(CareType.WATERING, 5L);
      when(reminderRepository.findByPlantIdAndEnabledTrue(1L))
          .thenReturn(List.of(watering, feeding, treatmentStep));
      when(reminderRepository.findByPlantIdAndEnabledTrue(2L)).thenReturn(List.of());

      List<CareLogResponse> result =
          service.logGroupCare(GROUP, request(GroupCareAction.WATERED), USER);

      ArgumentCaptor<CareLog> captor = ArgumentCaptor.forClass(CareLog.class);
      verify(careLogRepository, times(2)).save(captor.capture());
      assertThat(captor.getAllValues())
          .allSatisfy(
              l -> {
                assertThat(l.getCareType()).isEqualTo(CareType.WATERING);
                assertThat(l.getUserId()).isEqualTo(USER);
              })
          .extracting(CareLog::getPlantId)
          .containsExactly(1L, 2L);
      assertThat(result).hasSize(2);
      verify(reminderService).applyCompletionToReminder(eq(watering), any());
      verify(reminderService, never()).applyCompletionToReminder(eq(feeding), any());
      verify(reminderService, never()).applyCompletionToReminder(eq(treatmentStep), any());
    }

    @Test
    @DisplayName("FED maps to FERTILIZING")
    void fed() {
      stubGroup();
      when(plantRepository.findAllByUserIdAndGroupIdAndStatus(USER, GROUP, PlantStatus.ACTIVE))
          .thenReturn(List.of(plant(1L)));
      when(careLogRepository.save(any(CareLog.class))).thenAnswer(i -> i.getArgument(0));
      when(reminderRepository.findByPlantIdAndEnabledTrue(1L)).thenReturn(List.of());

      List<CareLogResponse> result =
          service.logGroupCare(GROUP, request(GroupCareAction.FED), USER);

      assertThat(result)
          .singleElement()
          .extracting(CareLogResponse::getCareType)
          .isEqualTo(CareType.FERTILIZING);
    }

    @Test
    @DisplayName("an empty group returns an empty list and writes nothing")
    void emptyGroup() {
      stubGroup();
      when(plantRepository.findAllByUserIdAndGroupIdAndStatus(USER, GROUP, PlantStatus.ACTIVE))
          .thenReturn(List.of());

      assertThat(service.logGroupCare(GROUP, request(GroupCareAction.WATERED), USER)).isEmpty();
      verify(careLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("another user's group is 404 and writes nothing")
    void foreignGroup() {
      when(groupRepository.findByIdAndUserId(GROUP, OTHER_USER)).thenReturn(Optional.empty());

      GroupCareRequest careRequest = request(GroupCareAction.WATERED);

      assertThatThrownBy(() -> service.logGroupCare(GROUP, careRequest, OTHER_USER))
          .isInstanceOf(ResourceNotFoundException.class);
      verify(careLogRepository, never()).save(any());
    }

    private void stubGroup() {
      when(groupRepository.findByIdAndUserId(GROUP, USER))
          .thenReturn(Optional.of(group(GROUP, USER, "Bed")));
    }
  }

  private static GroupCareRequest request(GroupCareAction action) {
    return new GroupCareRequest(action, null);
  }

  private static PlantGroup group(Long id, Long userId, String name) {
    return PlantGroup.builder().id(id).userId(userId).name(name).build();
  }

  private static Plant plant(Long id) {
    return Plant.builder()
        .id(id)
        .userId(USER)
        .nickname("P" + id)
        .status(PlantStatus.ACTIVE)
        .build();
  }

  private static Reminder reminder(CareType type, Long treatmentPlanId) {
    Reminder r = new Reminder();
    r.setCareType(type);
    r.setTreatmentPlanId(treatmentPlanId);
    return r;
  }
}
