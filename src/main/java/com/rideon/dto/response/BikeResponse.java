package com.rideon.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BikeResponse(
        UUID id,
        String make,
        String model,
        Integer year,
        Integer engineCc,
        String type,
        String nickname,
        String photoUrl,
        Instant createdAt
) {}