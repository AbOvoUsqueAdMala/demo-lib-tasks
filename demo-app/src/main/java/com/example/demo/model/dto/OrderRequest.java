package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderRequest(@NotBlank @Size(max = 200) String description,
		@Size(max = 100) String customerReference, @Size(max = 500) String internalComment) {}

