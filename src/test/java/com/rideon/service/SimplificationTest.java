package com.rideon.service;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Douglas-Peucker simplification preserving Z coordinates.
 * These are pure JTS unit tests with no Spring context.
 */
class SimplificationTest {

    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    private final double epsilon = 0.0001; // same as production RouteService

    @Test
    void simplify_dropsCollinearMiddlePoint_preservesZOnRetainedPoints() {
        // Three collinear points in 2D: A(0.0, 0.0, 100.0), B(0.5, 0.0, 200.0), C(1.0, 0.0, 300.0)
        // B lies exactly on the line between A and C in 2D, so it will be dropped
        LineString path = gf.createLineString(new Coordinate[]{
                new Coordinate(0.0, 0.0, 100.0),
                new Coordinate(0.5, 0.0, 200.0),
                new Coordinate(1.0, 0.0, 300.0)
        });

        LineString simplified = (LineString) org.locationtech.jts.simplify.DouglasPeuckerSimplifier
                .simplify(path, epsilon);

        assertThat(simplified.getNumPoints()).isEqualTo(2);
        assertThat(simplified.getCoordinateN(0).getZ()).isEqualTo(100.0);
        assertThat(simplified.getCoordinateN(1).getZ()).isEqualTo(300.0);
    }

    @Test
    void simplify_keepsAllPoints_whenNoneAreCollinear() {
        // Three points forming a clear bend: A(0.0, 0.0, 490.0), B(0.5, 1.0, 510.0), C(1.0, 0.0, 540.0)
        // The bend is large enough that B will never be dropped at epsilon=0.0001
        LineString path = gf.createLineString(new Coordinate[]{
                new Coordinate(0.0, 0.0, 490.0),
                new Coordinate(0.5, 1.0, 510.0),
                new Coordinate(1.0, 0.0, 540.0)
        });

        LineString simplified = (LineString) org.locationtech.jts.simplify.DouglasPeuckerSimplifier
                .simplify(path, epsilon);

        assertThat(simplified.getNumPoints()).isEqualTo(3);
        assertThat(simplified.getCoordinateN(0).getZ()).isEqualTo(490.0);
        assertThat(simplified.getCoordinateN(1).getZ()).isEqualTo(510.0);
        assertThat(simplified.getCoordinateN(2).getZ()).isEqualTo(540.0);
    }

    @Test
    void simplify_retainsFirstAndLast_whenAllIntermediatePointsAreCollinear() {
        // Five collinear points: (0,0,10), (0.25,0,20), (0.5,0,30), (0.75,0,40), (1.0,0,50)
        // All interior points lie on the line and will be dropped
        // Douglas-Peucker always retains first and last
        LineString path = gf.createLineString(new Coordinate[]{
                new Coordinate(0.0, 0.0, 10.0),
                new Coordinate(0.25, 0.0, 20.0),
                new Coordinate(0.5, 0.0, 30.0),
                new Coordinate(0.75, 0.0, 40.0),
                new Coordinate(1.0, 0.0, 50.0)
        });

        LineString simplified = (LineString) org.locationtech.jts.simplify.DouglasPeuckerSimplifier
                .simplify(path, epsilon);

        assertThat(simplified.getNumPoints()).isEqualTo(2);
        assertThat(simplified.getCoordinateN(0).getZ()).isEqualTo(10.0);
        assertThat(simplified.getCoordinateN(1).getZ()).isEqualTo(50.0);
    }

    @Test
    void simplify_doesNotCorruptZToNaN() {
        // Case 1 scenario - explicitly check that Z values are not corrupted to NaN
        LineString path = gf.createLineString(new Coordinate[]{
                new Coordinate(0.0, 0.0, 100.0),
                new Coordinate(0.5, 0.0, 200.0),
                new Coordinate(1.0, 0.0, 300.0)
        });

        LineString simplified = (LineString) org.locationtech.jts.simplify.DouglasPeuckerSimplifier
                .simplify(path, epsilon);

        assertThat(Double.isNaN(simplified.getCoordinateN(0).getZ())).isFalse();
        assertThat(Double.isNaN(simplified.getCoordinateN(1).getZ())).isFalse();
    }
}
