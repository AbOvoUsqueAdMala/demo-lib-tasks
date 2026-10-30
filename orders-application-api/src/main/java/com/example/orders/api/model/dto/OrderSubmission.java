package com.example.orders.api.model.dto;

public record OrderSubmission(
		String description, String customerReference, String internalComment) {}
