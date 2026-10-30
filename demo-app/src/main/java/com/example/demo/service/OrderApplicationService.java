package com.example.demo.service;

import com.example.demo.model.dto.OrderRequest;
import com.example.demo.model.dto.OrderResponse;
import com.example.demo.model.entity.OrderSubmissionRecord;
import com.example.demo.repository.OrderSubmissionRepository;
import com.example.demo.service.mapping.OrderApplicationMapper;
import com.example.orders.api.model.dto.ProcessedOrder;
import com.example.orders.api.service.OrderProcessor;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderApplicationService {
	private final Map<String, OrderProcessor> processors;
	private final OrderSubmissionRepository submissionRepository;
	private final OrderApplicationMapper orderMapper;

	public OrderApplicationService(List<OrderProcessor> processors,
			OrderSubmissionRepository submissionRepository, OrderApplicationMapper orderMapper) {
		this.processors = processors.stream().collect(Collectors.toUnmodifiableMap(
				OrderProcessor::code, Function.identity()));
		this.submissionRepository = submissionRepository;
		this.orderMapper = orderMapper;
	}

	@Transactional
	public OrderResponse create(String target, OrderRequest request) {
		var submission = orderMapper.toSubmission(request);
		ProcessedOrder order = processor(target).create(submission);
		submissionRepository.save(new OrderSubmissionRecord(order.id(), target, submission));
		return orderMapper.toResponse(order, submission.customerReference(),
				submission.internalComment(), target);
	}

	@Transactional(readOnly = true)
	public OrderResponse get(UUID orderId) {
		String target = submissionRepository.findById(orderId)
				.map(OrderSubmissionRecord::getProcessorCode).orElse("main");
		return get(target, orderId);
	}

	@Transactional(readOnly = true)
	public OrderResponse get(String target, UUID orderId) {
		var submission = submissionRepository.findById(orderId).orElse(null);
		if (submission != null && !submission.getProcessorCode().equals(target)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
		}
		try {
			ProcessedOrder order = processor(target).get(orderId);
			return orderMapper.toResponse(order,
					submission == null ? null : submission.getCustomerReference(),
					submission == null ? null : submission.getInternalComment(), target);
		} catch (NoSuchElementException failure) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found", failure);
		}
	}

	private OrderProcessor processor(String target) {
		OrderProcessor processor = processors.get(target);
		if (processor == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"Order processor is not installed: " + target);
		}
		return processor;
	}
}
