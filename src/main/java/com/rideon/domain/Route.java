package com.rideon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.LineString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_id")
    private Route canonical;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false, columnDefinition = "GEOMETRY(LINESTRING, 4326)")
    private LineString path;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(length = 20, nullable = false)
    private String visibility = "public";

    @Column(name = "is_protected", nullable = false)
    private boolean isProtected = false;

    @Column(name = "popularity_score", nullable = false)
    private Double popularityScore = 0.0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}