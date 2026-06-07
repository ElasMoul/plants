package com.plantpal.user.mapper;

import com.plantpal.user.dto.UserResponse;
import com.plantpal.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

  UserResponse toResponse(User user);
}
