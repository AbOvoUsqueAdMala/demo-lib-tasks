package com.example.extension.kz.controller;

import com.example.extension.kz.model.dto.KzOrderRequest;
import com.example.extension.kz.model.dto.KzOrderView;
import com.example.extension.kz.service.KzOrderStateService;
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
@RequestMapping({"/api/extension/kz/orders", "/api/extension/orders"})
@RequiredArgsConstructor
public class KzOrderController {
	private final KzOrderStateService orderStateService;

	@PostMapping
	public ResponseEntity<KzOrderView> create(
			@Valid @RequestBody KzOrderRequest request) {
		var customerOrder = orderStateService.create(request.description());
		return ResponseEntity.accepted()
				.location(URI.create("/api/extension/kz/orders/" + customerOrder.id())).body(customerOrder);
	}

	@GetMapping("/{id}")
	public KzOrderView get(@PathVariable UUID id) {
		return orderStateService.get(id);
	}
}
