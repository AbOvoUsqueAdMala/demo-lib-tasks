package com.example.adapters.kz.service;

import com.example.adapters.kz.service.mapping.KzOrderMapper;
import com.example.extension.kz.service.KzOrderStateService;
import com.example.orders.api.model.dto.OrderSubmission;
import com.example.orders.api.model.dto.ProcessedOrder;
import com.example.orders.api.service.OrderProcessor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class KzOrderAdapter implements OrderProcessor {
	private final KzOrderStateService orderStateService;
	private final KzOrderMapper orderMapper;

	@Override
	public String code() {
		return "kz";
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
