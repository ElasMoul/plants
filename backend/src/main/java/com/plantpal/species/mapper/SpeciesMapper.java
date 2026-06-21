package com.plantpal.species.mapper;

import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.entity.Species;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SpeciesMapper {

  // careCards is raw JSON (String) on the entity but List<CareCardDto> on the response --
  // parsed manually in SpeciesServiceImpl, same as IdentificationMapper ignores carePlan.
  @Mapping(target = "careCards", ignore = true)
  SpeciesResponse toResponse(Species species);
}
