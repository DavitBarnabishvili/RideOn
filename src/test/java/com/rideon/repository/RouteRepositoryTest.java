package com.rideon.repository;

import com.rideon.domain.Route;
import com.rideon.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
class RouteRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
    ).withInitScript("postgis-init.sql");

    @Autowired
    RouteRepository routeRepository;

    @Autowired
    UserRepository userRepository;

    private User user;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("rider@example.com");
        user.setPassword("hashed");
        user = userRepository.save(user);

        geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    private LineString buildLineString(double lon1, double lat1,
                                       double lon2, double lat2) {
        return geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(lon1, lat1),
                new Coordinate(lon2, lat2)
        });
    }

    private Route buildRoute(String title, String visibility,
                             double lon1, double lat1,
                             double lon2, double lat2) {
        Route route = new Route();
        route.setUser(user);
        route.setTitle(title);
        route.setVisibility(visibility);
        route.setPath(buildLineString(lon1, lat1, lon2, lat2));
        return route;
    }

    @Test
    void findByUserId_returnsRoutesForUser() {
        routeRepository.save(buildRoute("Mountain Pass", "public",
                44.79, 41.69, 44.82, 41.72));

        List<Route> routes = routeRepository.findByUserId(user.getId());

        assertThat(routes).hasSize(1);
        assertThat(routes.getFirst().getTitle()).isEqualTo("Mountain Pass");
    }

    @Test
    void findByIdAndUserId_returnsRoute_whenOwned() {
        Route saved = routeRepository.save(buildRoute("Coastal Run", "public",
                44.79, 41.69, 44.82, 41.72));

        assertThat(routeRepository.findByIdAndUserId(saved.getId(), user.getId()))
                .isPresent();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenNotOwned() {
        Route saved = routeRepository.save(buildRoute("Coastal Run", "public",
                44.79, 41.69, 44.82, 41.72));

        User other = new User();
        other.setEmail("other@example.com");
        other.setPassword("hashed");
        other = userRepository.save(other);

        assertThat(routeRepository.findByIdAndUserId(saved.getId(), other.getId()))
                .isEmpty();
    }

    @Test
    void findPublicRoutesNear_returnsNearbyRoutes() {
        // route near Tbilisi
        routeRepository.save(buildRoute("Tbilisi Loop", "public",
                44.79, 41.69, 44.82, 41.72));

        // route far away (London)
        routeRepository.save(buildRoute("London Route", "public",
                -0.12, 51.50, -0.10, 51.52));

        List<Route> nearby = routeRepository.findPublicRoutesNear(
                41.69, 44.79, 50000, 10);

        assertThat(nearby).hasSize(1);
        assertThat(nearby.getFirst().getTitle()).isEqualTo("Tbilisi Loop");
    }

    @Test
    void findPublicRoutesNear_excludesPrivateRoutes() {
        routeRepository.save(buildRoute("Secret Pass", "private",
                44.79, 41.69, 44.82, 41.72));

        List<Route> nearby = routeRepository.findPublicRoutesNear(
                41.69, 44.79, 50000, 10);

        assertThat(nearby).isEmpty();
    }
}