package com.example.adapters.az.service.mapping;

import com.example.extension.az.model.dto.AzOrderCommand;
import com.example.extension.az.model.dto.AzOrderView;
import com.example.orders.api.model.dto.OrderSubmission;
import com.example.orders.api.model.dto.ProcessedOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AzOrderMapper {
	@Mapping(target = "details", source = "description")
	AzOrderCommand toCommand(OrderSubmission submission);

	@Mapping(target = "description", source = "details")
	ProcessedOrder toProcessedOrder(AzOrderView order);
}
