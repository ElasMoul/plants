package com.plantpal.plant.mapper;

import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.entity.Plant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlantMapper {

  PlantResponse toResponse(Plant plant);

  // userId and status are set by the service layer — never in the mapper
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "status", ignore = true)
  Plant toEntity(CreatePlantRequest request);
}
