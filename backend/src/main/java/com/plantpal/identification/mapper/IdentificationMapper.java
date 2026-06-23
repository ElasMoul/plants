package com.plantpal.identification.mapper;

import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.entity.Identification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IdentificationMapper {

  @Mapping(target = "carePlan", ignore = true)
  @Mapping(target = "annotationRegions", ignore = true)
  @Mapping(target = "plantNetCandidates", ignore = true)
  IdentificationResponse toResponse(Identification identification);
}
