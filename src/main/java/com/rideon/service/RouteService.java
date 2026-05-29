package com.rideon.service;

import com.rideon.domain.Route;
import com.rideon.domain.User;
import com.rideon.dto.request.RouteRequest;
import com.rideon.dto.response.RouteResponse;
import com.rideon.exception.RouteNotFoundException;
import com.rideon.repository.RouteRepository;
import com.rideon.repository.UserRepository;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
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
    private final UserRepository userRepository;

    private final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326);

    public RouteResponse createRoute(String email, RouteRequest request) {
        User user = findUser(email);
        LineString path = toLineString(request.coordinates());

        Route route = new Route();
        route.setUser(user);
        route.setTitle(request.title());
        route.setDescription(request.description());
        route.setPath(path);
        route.setDistanceM(path.getLength() * 111_320);
        route.setVisibility(request.visibility() != null ? request.visibility() : "public");

        return toResponse(routeRepository.save(route));
    }

    public RouteResponse importGpx(String email, MultipartFile file,
                                   String visibility, String description) {

        if (visibility != null && !visibility.equals("public") && !visibility.equals("private")) {
            throw new IllegalArgumentException("Visibility must be 'public' or 'private'");
        }

        User user = findUser(email);

        GPX gpx;
        try {
            gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(file.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read GPX file", e);
        }

        List<double[]> coordinates = gpx.tracks()
                .flatMap(Track::segments)
                .flatMap(TrackSegment::points)
                .map(wp -> new double[]{
                        wp.getLongitude().doubleValue(),
                        wp.getLatitude().doubleValue()
                })
                .toList();

        if (coordinates.size() < 2) {
            throw new IllegalArgumentException("GPX file must contain at least 2 track points");
        }

        String title = gpx.tracks()
                .findFirst()
                .flatMap(Track::getName)
                .orElse("Imported route");

        LineString path = simplify(toLineString(coordinates));

        Route route = new Route();
        route.setUser(user);
        route.setTitle(title);
        route.setDescription(description);
        route.setPath(path);
        route.setDistanceM(path.getLength() * 111_320);
        route.setVisibility(visibility != null ? visibility : "public");

        return toResponse(routeRepository.save(route));
    }

    public byte[] exportGpx(String email, UUID routeId) {
        User user = findUser(email);
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeId));

        if (route.getVisibility().equals("private") && !route.getUser().getId().equals(user.getId())) {
            throw new RouteNotFoundException("Route not found: " + routeId);
        }

        GPX gpx = GPX.builder()
                .addTrack(track -> track
                        .name(route.getTitle())
                        .addSegment(seg -> {
                            for (Coordinate c : route.getPath().getCoordinates()) {
                                seg.addPoint(p -> p.lat(c.y).lon(c.x));
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

    public List<RouteResponse> getMyRoutes(String email) {
        User user = findUser(email);
        return routeRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RouteResponse> getRoutesNear(double lat, double lon,
                                             double radiusMeters, int limit) {
        return routeRepository.findPublicRoutesNear(lat, lon, radiusMeters, limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void deleteRoute(String email, UUID routeId) {
        User user = findUser(email);
        Route route = routeRepository.findByIdAndUserId(routeId, user.getId())
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeId));

        if (route.isProtected()) {
            throw new IllegalStateException("Protected routes cannot be deleted");
        }

        routeRepository.delete(route);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private LineString toLineString(List<double[]> coordinates) {
        Coordinate[] coords = coordinates.stream()
                .map(c -> new Coordinate(c[0], c[1]))
                .toArray(Coordinate[]::new);
        return geometryFactory.createLineString(coords);
    }

    private RouteResponse toResponse(Route route) {
        List<double[]> coordinates = Arrays.stream(route.getPath().getCoordinates())
                .map(c -> new double[]{c.x, c.y})
                .toList();

        return new RouteResponse(
                route.getId(),
                route.getUser().getId(),
                route.getTitle(),
                route.getDescription(),
                coordinates,
                route.getDistanceM(),
                route.getVisibility(),
                route.isProtected(),
                route.getPopularityScore(),
                route.getCreatedAt()
        );
    }

    private LineString simplify(LineString path) {
        // Douglas-Peucker: removes points deviating less than ~11m from the simplified line.
        // Epsilon in degrees — 0.0001° ≈ 11m
        var simplified = org.locationtech.jts.simplify.DouglasPeuckerSimplifier
                .simplify(path, 0.0001);
        return (LineString) simplified;
    }
}