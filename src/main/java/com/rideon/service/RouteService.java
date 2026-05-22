package com.rideon.service;

import com.rideon.domain.Route;
import com.rideon.domain.User;
import com.rideon.dto.request.RouteRequest;
import com.rideon.dto.response.RouteResponse;
import com.rideon.exception.RouteNotFoundException;
import com.rideon.repository.RouteRepository;
import com.rideon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        LineString path = toLineString(request.coordinates());

        Route route = new Route();
        route.setUser(user);
        route.setTitle(request.title());
        route.setDescription(request.description());
        route.setPath(path);
        route.setDistanceM(path.getLength() * 111_320); // rough degrees-to-meters, to be changed in phase 2
        route.setVisibility(request.visibility() != null ? request.visibility() : "public");

        return toResponse(routeRepository.save(route));
    }

    public List<RouteResponse> getMyRoutes(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        Route route = routeRepository.findByIdAndUserId(routeId, user.getId())
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeId));

        if (route.isProtected()) {
            throw new IllegalStateException("Protected routes cannot be deleted");
        }

        routeRepository.delete(route);
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
}