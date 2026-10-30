package com.example.demo.controller;

import com.example.demo.model.dto.OrderRequest;
import com.example.demo.model.dto.OrderView;
import com.example.demo.service.OrderStateService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
	private final OrderStateService orderStateService;

	@PostMapping
	public ResponseEntity<OrderView> create(@Valid @RequestBody OrderRequest request) {
		var customerOrder = orderStateService.create(request.description());
		return ResponseEntity.accepted()
				.location(URI.create("/api/orders/" + customerOrder.id())).body(customerOrder);
	}

	@GetMapping("/{id}")
	public OrderView get(@PathVariable UUID id) {
		return orderStateService.get(id);
	}
}

