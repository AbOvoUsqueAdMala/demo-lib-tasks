package com.example.extension.controller;

import com.example.extension.model.dto.ExtensionOrderRequest;
import com.example.extension.model.dto.ExtensionOrderView;
import com.example.extension.service.ExtensionOrderStateService;
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
@RequestMapping("/api/extension/orders")
@RequiredArgsConstructor
public class ExtensionOrderController {
	private final ExtensionOrderStateService orderStateService;

	@PostMapping
	public ResponseEntity<ExtensionOrderView> create(
			@Valid @RequestBody ExtensionOrderRequest request) {
		var customerOrder = orderStateService.create(request.description());
		return ResponseEntity.accepted()
				.location(URI.create("/api/extension/orders/" + customerOrder.id())).body(customerOrder);
	}

	@GetMapping("/{id}")
	public ExtensionOrderView get(@PathVariable UUID id) {
		return orderStateService.get(id);
	}
}

