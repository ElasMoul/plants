package com.plantpal.plantgroup.controller;

import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plantgroup.dto.AssignGroupRequest;
import com.plantpal.plantgroup.dto.CreateGroupRequest;
import com.plantpal.plantgroup.dto.GroupCareRequest;
import com.plantpal.plantgroup.dto.GroupResponse;
import com.plantpal.plantgroup.dto.RenameGroupRequest;
import com.plantpal.plantgroup.service.PlantGroupService;
import com.plantpal.reminder.dto.CareLogResponse;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Plant groups", description = "Group plants by room, bed or greenhouse")
@SecurityRequirement(name = "bearerAuth")
public class PlantGroupController {

  private final PlantGroupService groupService;

  public PlantGroupController(PlantGroupService groupService) {
    this.groupService = groupService;
  }

  @Operation(summary = "List the authenticated user's plant groups")
  @GetMapping("/groups")
  public ResponseEntity<ApiResponse<Page<GroupResponse>>> getGroups(
      @PageableDefault(size = 100, sort = "name", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(groupService.getUserGroups(getCurrentUserId(), pageable)));
  }

  @Operation(summary = "Create a plant group")
  @PostMapping("/groups")
  public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
      @Valid @RequestBody CreateGroupRequest request) {
    GroupResponse response = groupService.createGroup(request.getName(), getCurrentUserId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Group created"));
  }

  @Operation(summary = "Rename a plant group")
  @PutMapping("/groups/{id}")
  public ResponseEntity<ApiResponse<GroupResponse>> renameGroup(
      @PathVariable Long id, @Valid @RequestBody RenameGroupRequest request) {
    GroupResponse response = groupService.renameGroup(id, request.getName(), getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "Group renamed"));
  }

  @Operation(summary = "Delete a plant group; its plants are kept and just ungrouped")
  @DeleteMapping("/groups/{id}")
  public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
    groupService.deleteGroup(id, getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Log watering or feeding for every active plant in a group")
  @PostMapping("/groups/{id}/care")
  public ResponseEntity<ApiResponse<List<CareLogResponse>>> logGroupCare(
      @PathVariable Long id, @Valid @RequestBody GroupCareRequest request) {
    List<CareLogResponse> logs = groupService.logGroupCare(id, request, getCurrentUserId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(logs, "Care logged for group"));
  }

  @Operation(summary = "Move a plant into a group, or out of its group with a null groupId")
  @PutMapping("/plants/{plantId}/group")
  public ResponseEntity<ApiResponse<PlantResponse>> assignPlant(
      @PathVariable Long plantId, @RequestBody AssignGroupRequest request) {
    PlantResponse response =
        groupService.assignPlant(plantId, request.getGroupId(), getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
