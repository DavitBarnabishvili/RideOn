package com.rideon.dto.request;

import java.util.List;

/**
 * Partial update for a route (PATCH semantics).
 * <p>
 * A {@code null} field means "not provided — leave unchanged". A present value
 * (including an empty-string description) is applied. There is intentionally no
 * field for coordinates/path: route geometry is immutable post-creation —
 * changing the path requires creating a new route.
 * <p>
 * No bean-validation annotations here: "null is allowed, but blank-when-present
 * is rejected" does not map cleanly onto @NotBlank, so title/visibility are
 * validated in the service layer (consistent with import visibility handling).
 */
public record UpdateRouteRequest(
        String title,
        String description,
        String visibility
) {}