package com.rideon.service;

import com.rideon.domain.Route;
import com.rideon.domain.User;
import com.rideon.dto.request.RouteRequest;
import com.rideon.dto.request.UpdateRouteRequest;
import com.rideon.dto.response.RouteResponse;
import com.rideon.exception.RouteNotFoundException;
import com.rideon.repository.RouteRepository;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Length;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final UserService userService;
    private final GeometryFactory geometryFactory;

    @Transactional
    public RouteResponse createRoute(String email, RouteRequest request) {
        User user = userService.requireUser(email);
        // Manual route creation uses 2D coordinates only. Elevation will be
        // added via the OpenTopoData API integration in Phase 2.
        LineString path = toLineString2D(request.coordinates());

        Route route = new Route();
        route.setUser(user);
        route.setTitle(request.title());
        route.setDescription(request.description());
        route.setPath(path);
        route.setDistanceM(path.getLength() * 111_320);
        route.setVisibility(request.visibility() != null ? request.visibility() : "public");

        return toResponse(routeRepository.save(route));
    }

    @Transactional
    public RouteResponse importGpx(String email, MultipartFile file,
                                   String visibility, String description) {

        if (visibility != null) {
            validateVisibility(visibility);
        }

        User user = userService.requireUser(email);

        GPX gpx;
        try {
            gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(file.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read GPX file", e);
        }

        List<WayPoint> wayPoints = gpx.tracks()
                .flatMap(Track::segments)
                .flatMap(TrackSegment::points)
                .toList();

        if (wayPoints.size() < 2) {
            throw new IllegalArgumentException("GPX file must contain at least 2 track points");
        }

        String title = gpx.tracks()
                .findFirst()
                .flatMap(Track::getName)
                .orElse("Imported route");

        LineString path = simplify(toLineString3D(wayPoints));

        Route route = new Route();
        route.setUser(user);
        route.setTitle(title);
        route.setDescription(description);
        route.setPath(path);
        route.setDistanceM(path.getLength() * 111_320);
        route.setVisibility(visibility != null ? visibility : "public");
        computeElevation(route);

        return toResponse(routeRepository.save(route));
    }

    @Transactional(readOnly = true)
    public byte[] exportGpx(String email, UUID routeId) {
        User user = userService.requireUser(email);
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeId));

        if (route.getVisibility().equals("private") && !route.getUser().getId().equals(user.getId())) {
            throw new RouteNotFoundException("Route not found: " + routeId);
        }

        Coordinate[] coords = route.getPath().getCoordinates();

        // Include <ele> only when every point has valid elevation data.
        // Partial elevation produces spec-valid but consumer-unreliable GPX.
        // Routes without full elevation will be backfilled by the OpenTopoData
        // integration in Phase 2, at which point this guard becomes unnecessary.
        boolean allElevationsValid = Arrays.stream(coords)
                .allMatch(c -> !Double.isNaN(c.getZ()));

        GPX gpx = GPX.builder()
                .addTrack(track -> track
                        .name(route.getTitle())
                        .addSegment(seg -> {
                            for (Coordinate c : coords) {
                                if (allElevationsValid) {
                                    seg.addPoint(p -> p.lat(c.y).lon(c.x).ele(c.getZ()));
                                } else {
                                    seg.addPoint(p -> p.lat(c.y).lon(c.x));
                                }
                            }
                        }))
                .build();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GPX.Writer.DEFAULT.write(gpx, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize route to GPX", e);
        }
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> getMyRoutes(String email) {
        User user = userService.requireUser(email);
        return routeRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> getRoutesNear(double lat, double lon,
                                             double radiusMeters, int limit) {
        return routeRepository.findPublicRoutesNear(lat, lon, radiusMeters, limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> getPublicRoutesByUser(UUID userId) {
        // Public-only, always — even for the owner. Owners use /routes/my for
        // their full list. Unknown userId simply yields an empty list; Not
        // 404 here, to avoid confirming whether a user exists.
        return routeRepository.findByUserIdAndVisibility(userId, "public")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RouteResponse updateRoute(String email, UUID routeId, UpdateRouteRequest request) {
        User user = userService.requireUser(email);

        // Non-owner (or missing) route returns 404, never 403 — we never confirm
        // a route's existence to someone who doesn't own it.
        Route route = routeRepository.findByIdAndUserId(routeId, user.getId())
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeId));

        // Protected routes are community-owned: block all metadata
        // edits (mirrors deleteRoute) so an owner can't flip one to private and
        // hide it. Relax to per-field rules when the admin/role system exists.
        if (route.isProtected()) {
            throw new IllegalStateException("Protected routes cannot be modified");
        }

        // PATCH semantics: only non-null fields are applied. A present field is
        // validated; a null field is left unchanged. Geometry is not editable.
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new IllegalArgumentException("Title must not be blank");
            }
            route.setTitle(request.title());
        }

        if (request.description() != null) {
            route.setDescription(request.description());
        }

        if (request.visibility() != null) {
            validateVisibility(request.visibility());
            route.setVisibility(request.visibility());
        }

        return toResponse(routeRepository.save(route));
    }

    @Transactional
    public void deleteRoute(String email, UUID routeId) {
        User user = userService.requireUser(email);
        Route route = routeRepository.findByIdAndUserId(routeId, user.getId())
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeId));

        if (route.isProtected()) {
            throw new IllegalStateException("Protected routes cannot be deleted");
        }

        routeRepository.delete(route);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Builds a 3D LineString from [lon, lat] pairs for manually created routes.
     * Z is set to 0.0 as a placeholder — PostGIS requires a real number in the
     * LINESTRINGZ column and rejects NaN. Elevation is only meaningful when
     * elevationGainM is non-null; use that field as the authoritative signal,
     * not the Z value. Placeholder Z values will be replaced by real elevation
     * data from the OpenTopoData API integration in Phase 2.
     */
    private LineString toLineString2D(List<double[]> coordinates) {
        Coordinate[] coords = coordinates.stream()
                .map(c -> new Coordinate(c[0], c[1], 0.0))
                .toArray(Coordinate[]::new);
        return geometryFactory.createLineString(coords);
    }

    /**
     * Builds a 3D LineString from GPX WayPoints, preserving elevation where present.
     * Points without an elevation value get Z = NaN (JTS convention for missing Z).
     */
    private LineString toLineString3D(List<WayPoint> wayPoints) {
        Coordinate[] coords = wayPoints.stream()
                .map(wp -> {
                    double lon = wp.getLongitude().doubleValue();
                    double lat = wp.getLatitude().doubleValue();
                    double ele = wp.getElevation()
                            .map(Length::doubleValue)
                            .orElse(Double.NaN);
                    return new Coordinate(lon, lat, ele);
                })
                .toArray(Coordinate[]::new);
        return geometryFactory.createLineString(coords);
    }

    /**
     * Computes elevation gain and loss from Z coordinates and sets them on the route.
     * Skipped silently if any Z is NaN — this covers manually created routes and any
     * GPX file missing ele tags. Will become unreachable for GPX imports once
     * OpenTopoData backfill is in place (Phase 2).
     */
    private void computeElevation(Route route) {
        Coordinate[] coords = route.getPath().getCoordinates();
        boolean allValid = Arrays.stream(coords).allMatch(c -> !Double.isNaN(c.getZ()));
        if (!allValid) {
            return;
        }

        double gain = 0.0;
        double loss = 0.0;
        for (int i = 1; i < coords.length; i++) {
            double delta = coords[i].getZ() - coords[i - 1].getZ();
            if (delta > 0) gain += delta;
            else loss += Math.abs(delta);
        }

        route.setElevationGainM(gain);
        route.setElevationLossM(loss);
    }

    private RouteResponse toResponse(Route route) {
        boolean hasElevation = route.getElevationGainM() != null;

        List<Double[]> coordinates = Arrays.stream(route.getPath().getCoordinates())
                .map(c -> {
                    Double ele = hasElevation ? c.getZ() : null;
                    return new Double[]{c.x, c.y, ele};
                })
                .toList();

        return new RouteResponse(
                route.getId(),
                route.getUser().getId(),
                route.getTitle(),
                route.getDescription(),
                coordinates,
                route.getDistanceM(),
                route.getElevationGainM(),
                route.getElevationLossM(),
                route.getVisibility(),
                route.isProtected(),
                route.getPopularityScore(),
                route.getCreatedAt()
        );
    }

    private LineString simplify(LineString path) {
        var simplified = org.locationtech.jts.simplify.DouglasPeuckerSimplifier
                .simplify(path, 0.0001);
        return (LineString) simplified;
    }

    private void validateVisibility(String visibility) {
        if (!visibility.equals("public") && !visibility.equals("private")) {
            throw new IllegalArgumentException("Visibility must be 'public' or 'private'");
        }
    }
}