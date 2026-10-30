package com.example.adapters.az.service;

import com.example.adapters.az.service.mapping.AzOrderMapper;
import com.example.extension.az.service.AzOrderStateService;
import com.example.orders.api.model.dto.OrderSubmission;
import com.example.orders.api.model.dto.ProcessedOrder;
import com.example.orders.api.service.OrderProcessor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AzOrderAdapter implements OrderProcessor {
	private final AzOrderStateService orderStateService;
	private final AzOrderMapper orderMapper;

	@Override
	public String code() {
		return "az";
	}

	@Override
	public ProcessedOrder create(OrderSubmission submission) {
		return orderMapper.toProcessedOrder(
				orderStateService.create(orderMapper.toCommand(submission)));
	}

	@Override
	public ProcessedOrder get(UUID orderId) {
		return orderMapper.toProcessedOrder(orderStateService.get(orderId));
	}
}
