package com.example.adapters.kz.service.mapping;

import com.example.extension.kz.model.dto.KzOrderCommand;
import com.example.extension.kz.model.dto.KzOrderView;
import com.example.orders.api.model.dto.OrderSubmission;
import com.example.orders.api.model.dto.ProcessedOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface KzOrderMapper {
	@Mapping(target = "text", source = "description")
	KzOrderCommand toCommand(OrderSubmission submission);

	@Mapping(target = "description", source = "text")
	ProcessedOrder toProcessedOrder(KzOrderView order);
}
