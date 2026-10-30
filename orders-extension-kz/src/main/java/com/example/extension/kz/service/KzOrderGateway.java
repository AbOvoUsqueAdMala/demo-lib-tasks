package com.example.extension.kz.service;

import com.example.extension.kz.model.dto.KzOrderView;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class KzOrderGateway {
	private final RestClient restClient;

	public String register(KzOrderView customerOrder, String idempotencyKey) {
		var registration = restClient.post().uri("/kz/source/orders")
				.header("Idempotency-Key", idempotencyKey)
				.body(Map.of("id", customerOrder.id(), "description", customerOrder.description()))
				.retrieve().body(Registration.class);
		if (registration == null || registration.externalId() == null
				|| registration.externalId().isBlank() || registration.externalId().length() > 200) {
			throw new IllegalArgumentException("Invalid registration response");
		}
		return registration.externalId();
	}

	public String fetch(String externalId) {
		String payload = restClient.get().uri("/kz/source/orders/{id}", externalId)
				.retrieve().body(String.class);
		if (payload == null || payload.isBlank()) {
			throw new IllegalArgumentException("Empty order response");
		}
		return payload;
	}

	public String send(String payload, String idempotencyKey) {
		restClient.post().uri("/kz/destination/orders")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
		return "Delivered";
	}

	public record Registration(String externalId) {}
}
