package com.example.extension.kz.service;

import com.example.extension.kz.model.dto.KzOrderView;
import com.example.extension.kz.model.dto.KzOrderView.TaskView;
import com.example.extension.kz.model.entity.KzOrder;
import com.example.extension.kz.model.entity.KzTaskResult;
import com.example.extension.kz.repository.KzOrderRepository;
import com.example.extension.kz.repository.KzTaskResultRepository;
import com.example.extension.kz.tasks.KzOrderStep;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public class KzOrderStateService {
	private final KzOrderRepository orderRepository;
	private final KzTaskResultRepository taskResultRepository;
	private final SchedulerClient schedulerClient;

	@Transactional
	public KzOrderView create(String description) {
		KzOrder customerOrder = orderRepository.save(new KzOrder(description));
		schedule(customerOrder.getId(), KzOrderStep.REGISTER_ORDER);
		return view(customerOrder);
	}

	@Transactional(readOnly = true)
	public KzOrderView get(UUID orderId) {
		return view(orderRepository.findById(orderId).orElseThrow(this::notFound));
	}

	@Transactional
	public void complete(UUID orderId, KzOrderStep step, String externalId, String response) {
		KzOrder customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (!customerOrder.getStatus().equals(step.expectedStatus())) {
			return;
		}
		String payload = step == KzOrderStep.GET_ORDER ? response : customerOrder.getPayload();
		customerOrder.complete(step.completedStatus(), externalId, payload);
		taskResultRepository.save(new KzTaskResult(
				orderId, step.taskName(), "SUCCESS", response, null));
		if (step.next() != null) {
			schedule(orderId, step.next());
		}
	}

	@Transactional
	public void recordFailure(UUID orderId, KzOrderStep step, String error, boolean terminal) {
		KzOrder customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (!customerOrder.getStatus().equals(step.expectedStatus())) {
			return;
		}
		taskResultRepository.save(new KzTaskResult(
				orderId, step.taskName(), "FAILURE", null, error));
		if (terminal) {
			customerOrder.fail();
		}
	}

	@Transactional
	public void exhaustRetries(UUID orderId, KzOrderStep step) {
		KzOrder customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (customerOrder.getStatus().equals(step.expectedStatus())) {
			customerOrder.fail();
		}
	}

	private void schedule(UUID orderId, KzOrderStep step) {
		schedulerClient.scheduleIfNotExists(
				step.descriptor().instance(orderId.toString()).scheduledTo(Instant.now()));
	}

	private KzOrderView view(KzOrder customerOrder) {
		var history = taskResultRepository.findByOrderIdOrderById(customerOrder.getId()).stream()
				.map(result -> new TaskView(result.getTaskName(), result.getOutcome(),
						result.getResponse(), result.getError(), result.getPerformedAt()))
				.toList();
		return new KzOrderView(customerOrder.getId(), customerOrder.getDescription(),
				customerOrder.getStatus(), customerOrder.getExternalId(), customerOrder.getPayload(),
				customerOrder.getCreatedAt(), customerOrder.getUpdatedAt(), history);
	}

	private ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
	}
}
