package com.rideon.service;

import com.rideon.domain.Route;
import com.rideon.domain.User;
import com.rideon.dto.request.RouteRequest;
import com.rideon.dto.request.UpdateRouteRequest;
import com.rideon.dto.response.RouteResponse;
import com.rideon.exception.InvalidVisibilityException;
import com.rideon.exception.ProtectedRouteException;
import com.rideon.exception.RouteNotFoundException;
import com.rideon.repository.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
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
    @Mock private UserService userService;

    private RouteService routeService;

    private User user;
    private GeometryFactory gf;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("rider@example.com");
        user.setPassword("hashed");
        user.setId(UUID.randomUUID());
        gf = new GeometryFactory(new PrecisionModel(), 4326);
        routeService = new RouteService(routeRepository, userService, gf);
    }

    private RouteRequest buildRequest(String visibility) {
        return new RouteRequest(
                "Mountain Pass",
                "A great route",
                List.of(new double[]{44.79, 41.69}, new double[]{44.82, 41.72}),
                visibility
        );
    }

    /** 2D route — no elevation, Z will be NaN on all coordinates. */
    private Route buildSavedRoute() {
        LineString path = gf.createLineString(new Coordinate[]{
                new Coordinate(44.79, 41.69),
                new Coordinate(44.82, 41.72)
        });
        Route route = new Route();
        route.setUser(user);
        route.setTitle("Mountain Pass");
        route.setDescription("A great route");
        route.setPath(path);
        route.setVisibility("public");
        route.setPopularityScore(0.0);
        return route;
    }

    /** 3D route — elevation present on all coordinates. */
    private Route buildSavedRouteWithElevation() {
        LineString path = gf.createLineString(new Coordinate[]{
                new Coordinate(44.79, 41.69, 490),
                new Coordinate(44.82, 41.72, 540)
        });
        Route route = new Route();
        route.setUser(user);
        route.setTitle("Mountain Pass");
        route.setDescription("A great route");
        route.setPath(path);
        route.setVisibility("public");
        route.setPopularityScore(0.0);
        route.setElevationGainM(50.0);
        route.setElevationLossM(0.0);
        return route;
    }

    @Test
    void createRoute_returnsRouteResponse_whenUserExists() {
        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.save(any(Route.class))).thenReturn(buildSavedRoute());

        RouteResponse response = routeService.createRoute("rider@example.com", buildRequest("public"));

        assertThat(response.title()).isEqualTo("Mountain Pass");
        assertThat(response.visibility()).isEqualTo("public");
        verify(routeRepository).save(any(Route.class));
    }

    @Test
    void createRoute_defaultsToPublic_whenVisibilityIsNull() {
        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.save(any(Route.class))).thenReturn(buildSavedRoute());

        RouteResponse response = routeService.createRoute("rider@example.com", buildRequest(null));

        assertThat(response.visibility()).isEqualTo("public");
    }

    @Test
    void createRoute_hasNullElevation_whenCreatedManually() {
        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.save(any(Route.class))).thenReturn(buildSavedRoute());

        RouteResponse response = routeService.createRoute("rider@example.com", buildRequest("public"));

        // Manual routes have no elevation — all coordinate Z values should be null
        assertThat(response.coordinates()).allSatisfy(c -> assertThat(c[2]).isNull());
        assertThat(response.elevationGainM()).isNull();
        assertThat(response.elevationLossM()).isNull();
    }

    @Test
    void createRoute_throwsUsernameNotFoundException_whenUserNotFound() {
        when(userService.requireUser("ghost@example.com")).thenThrow(new UsernameNotFoundException("User not found: ghost@example.com"));

        assertThatThrownBy(() -> routeService.createRoute("ghost@example.com", buildRequest("public")))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(routeRepository, never()).save(any());
    }

    @Test
    void createRoute_throwsInvalidVisibilityException_whenVisibilityInvalid() {
        when(userService.requireUser("rider@example.com")).thenReturn(user);

        assertThatThrownBy(() -> routeService.createRoute("rider@example.com", buildRequest("banana")))
                .isInstanceOf(InvalidVisibilityException.class)
                .hasMessageContaining("Visibility");

        verify(routeRepository, never()).save(any());
    }

    @Test
    void createRoute_throwsIllegalArgumentException_whenCoordinatePairHasWrongLength() {
        when(userService.requireUser("rider@example.com")).thenReturn(user);

        var request = new RouteRequest(
                "Bad Route",
                "desc",
                List.of(new double[]{44.79}, new double[]{44.82, 41.72}),
                "public"
        );

        assertThatThrownBy(() -> routeService.createRoute("rider@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[lon, lat]");

        verify(routeRepository, never()).save(any());
    }

    @Test
    void getMyRoutes_returnsRoutesForUser() {
        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByUserId(any())).thenReturn(List.of(buildSavedRoute()));

        List<RouteResponse> routes = routeService.getMyRoutes("rider@example.com");

        assertThat(routes).hasSize(1);
        assertThat(routes.getFirst().title()).isEqualTo("Mountain Pass");
    }

    @Test
    void getMyRoutes_returnsElevationFields_whenRouteHasElevation() {
        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByUserId(any())).thenReturn(List.of(buildSavedRouteWithElevation()));

        RouteResponse response = routeService.getMyRoutes("rider@example.com").getFirst();

        assertThat(response.elevationGainM()).isEqualTo(50.0);
        assertThat(response.elevationLossM()).isEqualTo(0.0);
        assertThat(response.coordinates()).allSatisfy(c -> assertThat(c[2]).isNotNull());
    }

    @Test
    void deleteRoute_deletesRoute_whenOwnedAndNotProtected() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));

        routeService.deleteRoute("rider@example.com", routeId);

        verify(routeRepository).delete(route);
    }

    @Test
    void deleteRoute_throwsRouteNotFoundException_whenNotOwned() {
        UUID routeId = UUID.randomUUID();

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.deleteRoute("rider@example.com", routeId))
                .isInstanceOf(RouteNotFoundException.class);

        verify(routeRepository, never()).delete(any());
    }

    @Test
    void deleteRoute_throwsProtectedRouteException_whenRouteIsProtected() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();
        route.setProtected(true);

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));

        assertThatThrownBy(() -> routeService.deleteRoute("rider@example.com", routeId))
                .isInstanceOf(ProtectedRouteException.class)
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

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(privateRoute));

        assertThatThrownBy(() -> routeService.exportGpx("rider@example.com", routeId))
                .isInstanceOf(RouteNotFoundException.class);
    }

    // ── getRouteById ─────────────────────────────────────────────────────────

    @Test
    void getRouteById_returnsRoute_whenPublic() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(route));

        RouteResponse response = routeService.getRouteById("rider@example.com", routeId);

        assertThat(response.title()).isEqualTo("Mountain Pass");
    }

    @Test
    void getRouteById_returnsRoute_whenPrivateAndOwned() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();
        route.setVisibility("private");

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(route));

        RouteResponse response = routeService.getRouteById("rider@example.com", routeId);

        assertThat(response.visibility()).isEqualTo("private");
    }

    @Test
    void getRouteById_throwsRouteNotFoundException_whenPrivateAndNotOwned() {
        UUID routeId = UUID.randomUUID();

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");

        Route privateRoute = buildSavedRoute();
        privateRoute.setVisibility("private");
        privateRoute.setUser(otherUser);

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(privateRoute));

        assertThatThrownBy(() -> routeService.getRouteById("rider@example.com", routeId))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void getRouteById_returnsRoute_whenPublicAndAnonymous() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();

        when(routeRepository.findById(routeId)).thenReturn(Optional.of(route));

        RouteResponse response = routeService.getRouteById(null, routeId);

        assertThat(response.title()).isEqualTo("Mountain Pass");
    }

    @Test
    void getRouteById_throwsRouteNotFoundException_whenPrivateAndAnonymous() {
        UUID routeId = UUID.randomUUID();
        Route privateRoute = buildSavedRoute();
        privateRoute.setVisibility("private");

        when(routeRepository.findById(routeId)).thenReturn(Optional.of(privateRoute));

        assertThatThrownBy(() -> routeService.getRouteById(null, routeId))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void getRouteById_throwsRouteNotFoundException_whenRouteMissing() {
        UUID routeId = UUID.randomUUID();

        when(routeRepository.findById(routeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.getRouteById("rider@example.com", routeId))
                .isInstanceOf(RouteNotFoundException.class);
    }

    // ── updateRoute ──────────────────────────────────────────────────────────

    @Test
    void updateRoute_updatesProvidedFields_andLeavesNullsUnchanged() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute(); // title "Mountain Pass", desc "A great route", public

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));
        when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateRouteRequest("Renamed Pass", null, "private");
        RouteResponse response = routeService.updateRoute("rider@example.com", routeId, request);

        assertThat(response.title()).isEqualTo("Renamed Pass");
        assertThat(response.description()).isEqualTo("A great route"); // null → unchanged
        assertThat(response.visibility()).isEqualTo("private");
        verify(routeRepository).save(route);
    }

    @Test
    void updateRoute_throwsRouteNotFoundException_whenNotOwned() {
        UUID routeId = UUID.randomUUID();

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.empty());

        var request = new UpdateRouteRequest("Renamed", null, null);

        assertThatThrownBy(() -> routeService.updateRoute("rider@example.com", routeId, request))
                .isInstanceOf(RouteNotFoundException.class);

        verify(routeRepository, never()).save(any());
    }

    @Test
    void updateRoute_throwsInvalidVisibilityException_whenVisibilityInvalid() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));

        var request = new UpdateRouteRequest(null, null, "secret");

        assertThatThrownBy(() -> routeService.updateRoute("rider@example.com", routeId, request))
                .isInstanceOf(InvalidVisibilityException.class)
                .hasMessageContaining("Visibility");

        verify(routeRepository, never()).save(any());
    }

    @Test
    void updateRoute_throwsIllegalArgument_whenTitleBlank() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));

        var request = new UpdateRouteRequest("   ", null, null);

        assertThatThrownBy(() -> routeService.updateRoute("rider@example.com", routeId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        verify(routeRepository, never()).save(any());
    }

    @Test
    void updateRoute_throwsProtectedRouteException_whenRouteIsProtected() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();
        route.setProtected(true);

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));

        var request = new UpdateRouteRequest("New Title", null, null);

        assertThatThrownBy(() -> routeService.updateRoute("rider@example.com", routeId, request))
                .isInstanceOf(ProtectedRouteException.class)
                .hasMessageContaining("Protected");

        verify(routeRepository, never()).save(any());
    }

    @Test
    void updateRoute_clearsDescription_whenEmptyStringProvided() {
        UUID routeId = UUID.randomUUID();
        Route route = buildSavedRoute();

        when(userService.requireUser("rider@example.com")).thenReturn(user);
        when(routeRepository.findByIdAndUserId(routeId, user.getId()))
                .thenReturn(Optional.of(route));
        when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateRouteRequest(null, "", null);
        RouteResponse response = routeService.updateRoute("rider@example.com", routeId, request);

        assertThat(response.description()).isEmpty();
    }

    // ── getPublicRoutesByUser ────────────────────────────────────────────────

    @Test
    void getPublicRoutesByUser_returnsOnlyPublicRoutesForUser() {
        UUID targetUserId = UUID.randomUUID();
        when(routeRepository.findByUserIdAndVisibility(targetUserId, "public"))
                .thenReturn(List.of(buildSavedRoute()));

        List<RouteResponse> result = routeService.getPublicRoutesByUser(targetUserId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().visibility()).isEqualTo("public");
        verify(routeRepository).findByUserIdAndVisibility(targetUserId, "public");
    }

    @Test
    void getPublicRoutesByUser_returnsEmptyList_forUnknownUser() {
        UUID unknown = UUID.randomUUID();
        when(routeRepository.findByUserIdAndVisibility(unknown, "public"))
                .thenReturn(List.of());

        assertThat(routeService.getPublicRoutesByUser(unknown)).isEmpty();
    }
}