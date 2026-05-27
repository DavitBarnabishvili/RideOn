package com.rideon.service;

import com.rideon.domain.Route;
import com.rideon.domain.User;
import com.rideon.dto.request.RouteRequest;
import com.rideon.dto.response.RouteResponse;
import com.rideon.exception.RouteNotFoundException;
import com.rideon.repository.RouteRepository;
import com.rideon.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock private RouteRepository routeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private RouteService routeService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("rider@example.com");
        user.setPassword("hashed");
        user.setId(UUID.randomUUID());
    }

    private RouteRequest buildRequest(String visibility) {
        return new RouteRequest(
                "Mountain Pass",
                "A great route",
                List.of(new double[]{44.79, 41.69}, new double[]{44.82, 41.72}),
                visibility
        );
    }

    private Route buildSavedRoute() {
        RouteRequest request = buildRequest("public");
        org.locationtech.jts.geom.GeometryFactory gf =
                new org.locationtech.jts.geom.GeometryFactory(
                        new org.locationtech.jts.geom.PrecisionModel(), 4326);
        org.locationtech.jts.geom.LineString path = gf.createLineString(
                new org.locationtech.jts.geom.Coordinate[]{
                        new org.locationtech.jts.geom.Coordinate(44.79, 41.69),
                        new org.locationtech.jts.geom.Coordinate(44.82, 41.72)
                });
        Route route = new Route();
        route.setUser(user);
        route.setTitle(request.title());
        route.setDescription(request.description());
        route.setPath(path);
        route.setVisibility("public");
        route.setPopularityScore(0.0);
        return route;
    }

    @Test
    void createRoute_returnsRouteResponse_whenUserExists() {
        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(routeRepository.save(any(Route.class))).thenReturn(buildSavedRoute());

        RouteResponse response = routeService.createRoute("rider@example.com", buildRequest("public"));

        assertThat(response.title()).isEqualTo("Mountain Pass");
        assertThat(response.visibility()).isEqualTo("public");
        verify(routeRepository).save(any(Route.class));
    }

    @Test
    void createRoute_defaultsToPublic_whenVisibilityIsNull() {
        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(routeRepository.save(any(Route.class))).thenReturn(buildSavedRoute());

        RouteResponse response = routeService.createRoute("rider@example.com", buildRequest(null));

        assertThat(response.visibility()).isEqualTo("public");
    }

    @Test
    void createRoute_throwsUsernameNotFoundException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.createRoute("ghost@example.com", buildRequest("public")))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(routeRepository, never()).save(any());
    }

    @Test
    void getMyRoutes_returnsRoutesForUser() {
        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(routeRepository.findByUserId(any())).thenReturn(List.of(buildSavedRoute()));

        List<RouteResponse> routes = routeService.getMyRoutes("rider@example.com");

        assertThat(routes).hasSize(1);
        assertThat(routes.getFirst().title()).isEqualTo("Mountain Pass");
    }

    @Test
    void deleteRoute_deletesRoute_whenOwnedAndNotProtected() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));

        routeService.deleteRoute("rider@example.com", routeId);

        verify(routeRepository).delete(route);
    }

    @Test
    void deleteRoute_throwsRouteNotFoundException_whenNotOwned() {
        UUID routeId = UUID.randomUUID();

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.deleteRoute("rider@example.com", routeId))
                .isInstanceOf(RouteNotFoundException.class);

        verify(routeRepository, never()).delete(any());
    }

    @Test
    void deleteRoute_throwsIllegalStateException_whenRouteIsProtected() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();
        route.setProtected(true);

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));

        assertThatThrownBy(() -> routeService.deleteRoute("rider@example.com", routeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Protected");

        verify(routeRepository, never()).delete(any());
    }

    @Test
    void exportGpx_throwsRouteNotFoundException_whenRouteIsPrivateAndNotOwned() {
        UUID routeId = UUID.randomUUID();

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");

        Route privateRoute = buildSavedRoute();
        privateRoute.setVisibility("private");
        privateRoute.setUser(otherUser);

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(privateRoute));

        assertThatThrownBy(() -> routeService.exportGpx("rider@example.com", routeId))
                .isInstanceOf(RouteNotFoundException.class);
    }
}