package com.example.extension.service;

import com.example.extension.model.dto.ExtensionOrderView;
import com.example.extension.tasks.ExtensionOrderStep;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor
public class ExtensionOrderWorkflow {
	private final ExtensionOrderStateService orderStateService;
	private final ExtensionOrderGateway orderGateway;

	public void execute(UUID orderId, ExtensionOrderStep step) {
		ExtensionOrderView customerOrder = orderStateService.get(orderId);
		if (!customerOrder.status().equals(step.expectedStatus())) {
			return;
		}
		try {
			perform(customerOrder, step);
		} catch (RestClientResponseException failure) {
			boolean retryable = failure.getStatusCode().is5xxServerError()
					|| failure.getStatusCode().value() == 429
					|| failure.getStatusCode().value() == 408;
			orderStateService.recordFailure(orderId, step,
					"HTTP " + failure.getStatusCode().value(), !retryable);
			if (retryable) {
				throw failure;
			}
		} catch (ResourceAccessException failure) {
			orderStateService.recordFailure(orderId, step, "HTTP connection or timeout failure", false);
			throw failure;
		} catch (RestClientException | IllegalArgumentException failure) {
			orderStateService.recordFailure(orderId, step, "Invalid external response", true);
		}
	}

	private void perform(ExtensionOrderView customerOrder, ExtensionOrderStep step) {
		String idempotencyKey = step.taskName() + ":" + customerOrder.id();
		String externalId = customerOrder.externalId();
		String response;
		switch (step) {
			case REGISTER_ORDER -> {
				externalId = orderGateway.register(customerOrder, idempotencyKey);
				response = externalId;
			}
			case GET_ORDER -> response = orderGateway.fetch(externalId);
			case SEND_ORDER -> response = orderGateway.send(customerOrder.payload(), idempotencyKey);
			default -> throw new IllegalArgumentException("Unknown step");
		}
		orderStateService.complete(customerOrder.id(), step, externalId, response);
	}
}

