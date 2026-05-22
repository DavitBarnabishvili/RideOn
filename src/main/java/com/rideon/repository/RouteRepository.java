package com.rideon.repository;

import com.rideon.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    List<Route> findByUserId(UUID userId);

    Optional<Route> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            SELECT r.* FROM routes r
            WHERE r.visibility = 'public'
            AND ST_DWithin(
                r.path::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :radiusMeters
            )
            ORDER BY r.popularity_score DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Route> findPublicRoutesNear(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit
    );
}