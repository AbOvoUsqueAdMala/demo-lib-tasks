package com.example.extension.kz.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KzOrderRequest(@NotBlank @Size(max = 200) String description) {}
