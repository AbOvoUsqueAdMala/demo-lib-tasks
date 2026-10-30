package com.example.extension.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExtensionOrderRequest(@NotBlank @Size(max = 200) String description) {}

