package com.plantpal.species.mapper;

import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.entity.Species;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SpeciesMapper {

  SpeciesResponse toResponse(Species species);
}
