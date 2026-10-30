package com.example.demo.service;

import com.example.demo.model.dto.OrderView;
import com.example.demo.model.dto.OrderView.TaskView;
import com.example.demo.model.entity.Order;
import com.example.demo.model.entity.TaskResult;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.TaskResultRepository;
import com.example.demo.tasks.OrderStep;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public class OrderStateService {
	private final OrderRepository orderRepository;
	private final TaskResultRepository taskResultRepository;
	private final SchedulerClient schedulerClient;

	@Transactional
	public OrderView create(String description) {
		Order customerOrder = orderRepository.save(new Order(description));
		schedule(customerOrder.getId(), OrderStep.REGISTER_ORDER);
		return view(customerOrder);
	}

	@Transactional(readOnly = true)
	public OrderView get(UUID orderId) {
		return view(orderRepository.findById(orderId).orElseThrow(this::notFound));
	}

	@Transactional
	public void complete(UUID orderId, OrderStep step, String externalId, String response) {
		Order customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (!customerOrder.getStatus().equals(step.expectedStatus())) {
			return;
		}
		String payload = step == OrderStep.GET_ORDER ? response : customerOrder.getPayload();
		customerOrder.complete(step.completedStatus(), externalId, payload);
		taskResultRepository.save(new TaskResult(
				orderId, step.taskName(), "SUCCESS", response, null));
		if (step.next() != null) {
			schedule(orderId, step.next());
		}
	}

	@Transactional
	public void recordFailure(UUID orderId, OrderStep step, String error, boolean terminal) {
		Order customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (!customerOrder.getStatus().equals(step.expectedStatus())) {
			return;
		}
		taskResultRepository.save(new TaskResult(
				orderId, step.taskName(), "FAILURE", null, error));
		if (terminal) {
			customerOrder.fail();
		}
	}

	@Transactional
	public void exhaustRetries(UUID orderId, OrderStep step) {
		Order customerOrder = orderRepository.findForUpdate(orderId).orElseThrow(this::notFound);
		if (customerOrder.getStatus().equals(step.expectedStatus())) {
			customerOrder.fail();
		}
	}

	private void schedule(UUID orderId, OrderStep step) {
		schedulerClient.scheduleIfNotExists(
				step.descriptor().instance(orderId.toString()).scheduledTo(Instant.now()));
	}

	private OrderView view(Order customerOrder) {
		var history = taskResultRepository.findByOrderIdOrderById(customerOrder.getId()).stream()
				.map(result -> new TaskView(result.getTaskName(), result.getOutcome(),
						result.getResponse(), result.getError(), result.getPerformedAt()))
				.toList();
		return new OrderView(customerOrder.getId(), customerOrder.getDescription(),
				customerOrder.getStatus(), customerOrder.getExternalId(), customerOrder.getPayload(),
				customerOrder.getCreatedAt(), customerOrder.getUpdatedAt(), history);
	}

	private ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
	}
}

