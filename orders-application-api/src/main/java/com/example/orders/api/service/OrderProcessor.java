package com.example.orders.api.service;

import com.example.orders.api.model.dto.OrderSubmission;
import com.example.orders.api.model.dto.ProcessedOrder;
import java.util.UUID;

public interface OrderProcessor {
	String code();

	ProcessedOrder create(OrderSubmission submission);

	ProcessedOrder get(UUID orderId);
}
