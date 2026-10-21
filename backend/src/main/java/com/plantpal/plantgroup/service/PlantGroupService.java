package com.plantpal.plantgroup.service;

import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plantgroup.dto.GroupCareRequest;
import com.plantpal.plantgroup.dto.GroupResponse;
import com.plantpal.reminder.dto.CareLogResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlantGroupService {

  GroupResponse createGroup(String name, Long userId);

  GroupResponse renameGroup(Long groupId, String name, Long userId);

  void deleteGroup(Long groupId, Long userId);

  Page<GroupResponse> getUserGroups(Long userId, Pageable pageable);

  PlantResponse assignPlant(Long plantId, Long groupId, Long userId);

  List<CareLogResponse> logGroupCare(Long groupId, GroupCareRequest request, Long userId);
}
