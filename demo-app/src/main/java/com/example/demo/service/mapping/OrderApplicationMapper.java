package com.example.demo.service.mapping;

import com.example.demo.model.dto.OrderRequest;
import com.example.demo.model.dto.OrderResponse;
import com.example.demo.model.dto.OrderView;
import com.example.orders.api.model.dto.OrderSubmission;
import com.example.orders.api.model.dto.ProcessedOrder;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderApplicationMapper {
	OrderSubmission toSubmission(OrderRequest request);

	ProcessedOrder toProcessedOrder(OrderView order);

	OrderResponse toResponse(ProcessedOrder order, String customerReference,
			String internalComment, String target);
}
