package com.example.demo.service;

import com.example.demo.service.mapping.OrderApplicationMapper;
import com.example.orders.api.model.dto.OrderSubmission;
import com.example.orders.api.model.dto.ProcessedOrder;
import com.example.orders.api.service.OrderProcessor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MainOrderProcessor implements OrderProcessor {
	private final OrderStateService orderStateService;
	private final OrderApplicationMapper orderMapper;

	@Override
	public String code() {
		return "main";
	}

	@Override
	public ProcessedOrder create(OrderSubmission submission) {
		return orderMapper.toProcessedOrder(orderStateService.create(submission.description()));
	}

	@Override
	public ProcessedOrder get(UUID orderId) {
		return orderMapper.toProcessedOrder(orderStateService.get(orderId));
	}
}
