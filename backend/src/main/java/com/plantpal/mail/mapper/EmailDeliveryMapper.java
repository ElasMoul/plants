package com.plantpal.mail.mapper;

import com.plantpal.mail.dto.EmailDeliveryView;
import com.plantpal.mail.entity.EmailOutbox;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface EmailDeliveryMapper {

  EmailDeliveryView toView(EmailOutbox row);
}
