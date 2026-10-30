package com.example.extension.service;

import com.example.extension.model.dto.ExtensionOrderView;
import com.example.extension.model.dto.ExtensionOrderView.TaskView;
import com.example.extension.model.entity.ExtensionOrder;
import com.example.extension.model.entity.ExtensionTaskResult;
import com.example.extension.repository.ExtensionOrderRepository;
import com.example.extension.repository.ExtensionTaskResultRepository;
import com.example.extension.tasks.ExtensionOrderStep;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public class ExtensionOrderStateService {
	private final ExtensionOrderRepository orderRepository;
	private final ExtensionTaskResultRepository taskResultRepository;
	private final SchedulerClient schedulerClient;

	@Transactional
	public ExtensionOrderView create(String description) {
		ExtensionOrder customerOrder = orderRepository.save(new ExtensionOrder(description));
		schedule(customerOrder.getId(), ExtensionOrderStep.REGISTER_ORDER);
		return view(customerOrder);
	}

	@Transactional(readOnly = true)
	public ExtensionOrderView get(UUID orderId) {
		return view(orderRepository.findById(orderId).orElseThrow(this::notFound));
	}

	@Transactional
	public void complete(UUID orderId, ExtensionOrderStep step, String externalId, String response) {
		ExtensionOrder customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (!customerOrder.getStatus().equals(step.expectedStatus())) {
			return;
		}
		String payload = step == ExtensionOrderStep.GET_ORDER ? response : customerOrder.getPayload();
		customerOrder.complete(step.completedStatus(), externalId, payload);
		taskResultRepository.save(new ExtensionTaskResult(
				orderId, step.taskName(), "SUCCESS", response, null));
		if (step.next() != null) {
			schedule(orderId, step.next());
		}
	}

	@Transactional
	public void recordFailure(UUID orderId, ExtensionOrderStep step, String error, boolean terminal) {
		ExtensionOrder customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (!customerOrder.getStatus().equals(step.expectedStatus())) {
			return;
		}
		taskResultRepository.save(new ExtensionTaskResult(
				orderId, step.taskName(), "FAILURE", null, error));
		if (terminal) {
			customerOrder.fail();
		}
	}

	@Transactional
	public void exhaustRetries(UUID orderId, ExtensionOrderStep step) {
		ExtensionOrder customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (customerOrder.getStatus().equals(step.expectedStatus())) {
			customerOrder.fail();
		}
	}

	private void schedule(UUID orderId, ExtensionOrderStep step) {
		schedulerClient.scheduleIfNotExists(
				step.descriptor().instance(orderId.toString()).scheduledTo(Instant.now()));
	}

	private ExtensionOrderView view(ExtensionOrder customerOrder) {
		var history = taskResultRepository.findByOrderIdOrderById(customerOrder.getId()).stream()
				.map(result -> new TaskView(result.getTaskName(), result.getOutcome(),
						result.getResponse(), result.getError(), result.getPerformedAt()))
				.toList();
		return new ExtensionOrderView(customerOrder.getId(), customerOrder.getDescription(),
				customerOrder.getStatus(), customerOrder.getExternalId(), customerOrder.getPayload(),
				customerOrder.getCreatedAt(), customerOrder.getUpdatedAt(), history);
	}

	private ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
	}
}

