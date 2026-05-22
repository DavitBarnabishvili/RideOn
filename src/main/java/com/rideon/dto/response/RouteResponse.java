package com.rideon.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        UUID userId,
        String title,
        String description,
        List<double[]> coordinates,
        Double distanceM,
        String visibility,
        boolean isProtected,
        Double popularityScore,
        Instant createdAt
) {}