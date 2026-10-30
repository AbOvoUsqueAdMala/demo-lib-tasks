package com.example.demo.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, String description, String customerReference,
		String internalComment, String target, String status, String externalId,
		String payload, Instant createdAt, Instant updatedAt, List<TaskView> history) {
	public record TaskView(String taskName, String outcome, String response,
			String error, Instant performedAt) {}
}
