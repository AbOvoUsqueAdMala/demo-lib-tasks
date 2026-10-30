package com.example.demo.controller;

import com.example.demo.model.dto.OrderRequest;
import com.example.demo.model.dto.OrderResponse;
import com.example.demo.service.OrderApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {
	private final OrderApplicationService orderApplicationService;

	@PostMapping("/orders")
	public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request,
			@RequestParam(defaultValue = "main") String target) {
		return accepted(target, request);
	}

	@GetMapping("/orders/{id}")
	public OrderResponse get(@PathVariable UUID id) {
		return orderApplicationService.get(id);
	}

	@PostMapping("/extension/{target:kz|az}/orders")
	public ResponseEntity<OrderResponse> createExtension(@PathVariable String target,
			@Valid @RequestBody OrderRequest request) {
		return accepted(target, request);
	}

	@GetMapping("/extension/{target:kz|az}/orders/{id}")
	public OrderResponse getExtension(@PathVariable String target, @PathVariable UUID id) {
		return orderApplicationService.get(target, id);
	}

	@PostMapping("/extension/orders")
	public ResponseEntity<OrderResponse> createLegacyKz(@Valid @RequestBody OrderRequest request) {
		return accepted("kz", request);
	}

	@GetMapping("/extension/orders/{id}")
	public OrderResponse getLegacyKz(@PathVariable UUID id) {
		return orderApplicationService.get("kz", id);
	}

	private ResponseEntity<OrderResponse> accepted(String target, OrderRequest request) {
		OrderResponse order = orderApplicationService.create(target, request);
		return ResponseEntity.accepted().location(URI.create("/api/orders/" + order.id())).body(order);
	}
}
