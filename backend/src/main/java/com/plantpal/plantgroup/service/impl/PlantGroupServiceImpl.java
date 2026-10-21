package com.plantpal.plantgroup.service.impl;

import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.mapper.PlantMapper;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plantgroup.dto.GroupCareRequest;
import com.plantpal.plantgroup.dto.GroupResponse;
import com.plantpal.plantgroup.entity.PlantGroup;
import com.plantpal.plantgroup.repository.PlantGroupRepository;
import com.plantpal.plantgroup.service.PlantGroupService;
import com.plantpal.reminder.dto.CareLogResponse;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.shared.util.HtmlSanitizer;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlantGroupServiceImpl implements PlantGroupService {

  private static final Logger log = LoggerFactory.getLogger(PlantGroupServiceImpl.class);

  private static final String PLANTS_CACHE = "plants";
  private static final String GARDEN_CACHE = "garden";
  private static final String GROUP_NOT_FOUND = "Group not found";
  private static final String PLANT_NOT_FOUND = "Plant not found";

  private final PlantGroupRepository groupRepository;
  private final PlantRepository plantRepository;
  private final PlantMapper plantMapper;
  private final CareLogRepository careLogRepository;
  private final ReminderRepository reminderRepository;
  private final ReminderService reminderService;

  public PlantGroupServiceImpl(
      PlantGroupRepository groupRepository,
      PlantRepository plantRepository,
      PlantMapper plantMapper,
      CareLogRepository careLogRepository,
      ReminderRepository reminderRepository,
      ReminderService reminderService) {
    this.groupRepository = groupRepository;
    this.plantRepository = plantRepository;
    this.plantMapper = plantMapper;
    this.careLogRepository = careLogRepository;
    this.reminderRepository = reminderRepository;
    this.reminderService = reminderService;
  }

  @Override
  @Transactional
  public GroupResponse createGroup(String name, Long userId) {
    PlantGroup group = PlantGroup.builder().userId(userId).name(cleanName(name)).build();
    group = groupRepository.save(group);
    log.info("Plant group created: id={}, userId={}", group.getId(), userId);
    return toResponse(group, 0);
  }

  @Override
  @Transactional
  public GroupResponse renameGroup(Long groupId, String name, Long userId) {
    PlantGroup group = findOwnedGroup(groupId, userId);
    group.setName(cleanName(name));
    group = groupRepository.save(group);
    return toResponse(group, countActive(group.getId(), userId));
  }

  @Override
  @Transactional
  @Caching(
      evict = {
        @CacheEvict(value = PLANTS_CACHE, allEntries = true),
        @CacheEvict(value = GARDEN_CACHE, key = "#userId")
      })
  public void deleteGroup(Long groupId, Long userId) {
    PlantGroup group = findOwnedGroup(groupId, userId);
    // plants.group_id is ON DELETE SET NULL: only the grouping is removed, never the plants.
    groupRepository.delete(group);
    log.info("Plant group deleted: id={}, userId={}", groupId, userId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<GroupResponse> getUserGroups(Long userId, Pageable pageable) {
    return groupRepository
        .findAllByUserId(userId, pageable)
        .map(group -> toResponse(group, countActive(group.getId(), userId)));
  }

  @Override
  @Transactional
  @Caching(
      evict = {
        @CacheEvict(value = PLANTS_CACHE, allEntries = true),
        @CacheEvict(value = GARDEN_CACHE, key = "#userId")
      })
  public PlantResponse assignPlant(Long plantId, Long groupId, Long userId) {
    Plant plant =
        plantRepository
            .findByIdAndUserIdAndStatus(plantId, userId, PlantStatus.ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException(PLANT_NOT_FOUND));
    if (groupId != null) {
      findOwnedGroup(groupId, userId);
    }
    plant.setGroupId(groupId);
    return plantMapper.toResponse(plantRepository.save(plant));
  }

  @Override
  @Transactional
  @Caching(
      evict = {
        @CacheEvict(value = PLANTS_CACHE, allEntries = true),
        @CacheEvict(value = GARDEN_CACHE, key = "#userId")
      })
  public List<CareLogResponse> logGroupCare(Long groupId, GroupCareRequest request, Long userId) {
    findOwnedGroup(groupId, userId);
    CareType careType = request.getAction().careType();
    Instant performedAt = Instant.now();
    String notes = request.getNotes() == null ? null : HtmlSanitizer.sanitize(request.getNotes());

    List<Plant> plants =
        plantRepository.findAllByUserIdAndGroupIdAndStatus(userId, groupId, PlantStatus.ACTIVE);
    List<CareLogResponse> logged =
        plants.stream()
            .map(plant -> logPlantCare(plant, careType, notes, performedAt, userId))
            .toList();
    log.info(
        "Group care logged: groupId={}, careType={}, plants={}, userId={}",
        groupId,
        careType,
        logged.size(),
        userId);
    return logged;
  }

  private CareLogResponse logPlantCare(
      Plant plant, CareType careType, String notes, Instant performedAt, Long userId) {
    CareLog careLog =
        careLogRepository.save(
            CareLog.builder()
                .plantId(plant.getId())
                .userId(userId)
                .careType(careType)
                .notes(notes)
                .performedAt(performedAt)
                .build());
    // Reschedule the plant's recurring reminder of this type, as the single "mark done" flow does.
    // Treatment-step reminders are left alone: completing them is a treatment decision.
    reminderRepository.findByPlantIdAndEnabledTrue(plant.getId()).stream()
        .filter(r -> r.getCareType() == careType && r.getTreatmentPlanId() == null)
        .forEach(r -> reminderService.applyCompletionToReminder(r, performedAt));
    return CareLogResponse.builder()
        .id(careLog.getId())
        .plantId(plant.getId())
        .plantNickname(plant.getNickname())
        .careType(careLog.getCareType())
        .notes(careLog.getNotes())
        .performedAt(careLog.getPerformedAt())
        .build();
  }

  private PlantGroup findOwnedGroup(Long groupId, Long userId) {
    return groupRepository
        .findByIdAndUserId(groupId, userId)
        .orElseThrow(() -> new ResourceNotFoundException(GROUP_NOT_FOUND));
  }

  private long countActive(Long groupId, Long userId) {
    return plantRepository.countByUserIdAndGroupIdAndStatus(userId, groupId, PlantStatus.ACTIVE);
  }

  private String cleanName(String raw) {
    String name = raw == null ? "" : HtmlSanitizer.sanitize(raw).trim();
    if (name.isEmpty()) {
      throw new ValidationException("Group name must not be blank");
    }
    return name;
  }

  private GroupResponse toResponse(PlantGroup group, long plantCount) {
    return GroupResponse.builder()
        .id(group.getId())
        .name(group.getName())
        .plantCount(plantCount)
        .createdAt(group.getCreatedAt())
        .build();
  }
}
