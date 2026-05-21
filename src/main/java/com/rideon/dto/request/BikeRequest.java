package com.rideon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BikeRequest(
        @NotBlank String make,
        @NotBlank String model,
        @NotNull @Positive Integer year,
        @NotNull @Positive Integer engineCc,
        String type,
        String nickname
) {}