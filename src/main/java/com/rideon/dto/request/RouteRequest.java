package com.rideon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RouteRequest(
        @NotBlank String title,
        String description,
        @NotNull @Size(min = 2, message = "Route should contain at least 2 points") List<double[]> coordinates,
        String visibility
) {}