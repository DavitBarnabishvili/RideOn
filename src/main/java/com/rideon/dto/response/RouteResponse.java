package com.rideon.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        UUID userId,
        String title,
        String description,
        // Each element is [lon, lat, ele]. ele is null when elevation data is
        // not yet available (pre-OpenTopoData integration). Double[] (boxed)
        // so the third element can be null rather than NaN.
        List<Double[]> coordinates,
        Double distanceM,
        Double elevationGainM,
        Double elevationLossM,
        String visibility,
        boolean isProtected,
        Double popularityScore,
        Instant createdAt
) {}