package com.example.orders.api.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcessedOrder(UUID id, String description, String status, String externalId,
		String payload, Instant createdAt, Instant updatedAt, List<TaskResult> history) {
	public record TaskResult(String taskName, String outcome, String response,
			String error, Instant performedAt) {}
}
