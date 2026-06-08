package com.rideon.service;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GpxServiceTest {

    @Test
    void parseGpx_extractsTrackPoints() throws Exception {
        byte[] gpxBytes = getClass().getResourceAsStream("/test-route.gpx").readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-route.gpx", "application/gpx+xml", gpxBytes);

        GPX gpx = GPX.Reader.DEFAULT.read(file.getInputStream());

        long pointCount = gpx.tracks()
                .flatMap(Track::segments)
                .flatMap(TrackSegment::points)
                .count();

        assertThat(pointCount).isEqualTo(4);
    }

    @Test
    void parseGpx_extractsTrackName() throws Exception {
        byte[] gpxBytes = getClass().getResourceAsStream("/test-route.gpx").readAllBytes();

        GPX gpx = GPX.Reader.DEFAULT.read(new java.io.ByteArrayInputStream(gpxBytes));

        String name = gpx.tracks()
                .findFirst()
                .flatMap(Track::getName)
                .orElse("");

        assertThat(name).isEqualTo("Tbilisi to Mtskheta");
    }

    @Test
    void exportGpx_producesValidGpxXml() throws Exception {
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        LineString path = gf.createLineString(new Coordinate[]{
                new Coordinate(44.8337, 41.6941),
                new Coordinate(44.7800, 41.7200),
                new Coordinate(44.7212, 41.8347)
        });

        GPX gpx = GPX.builder()
                .addTrack(track -> track
                        .name("Test Route")
                        .addSegment(seg -> {
                            for (Coordinate c : path.getCoordinates()) {
                                seg.addPoint(p -> p.lat(c.y).lon(c.x));
                            }
                        }))
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GPX.Writer.DEFAULT.write(gpx, out);
        String xml = out.toString();

        assertThat(xml).contains("<gpx");
        assertThat(xml).contains("<trk>");
        assertThat(xml).contains("Test Route");
        assertThat(xml).contains("trkpt");
    }

    @Test
    void parseGpx_withNoTracks_hasNoPoints() throws Exception {
        String emptyGpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
        </gpx>
        """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.gpx", "application/gpx+xml", emptyGpx.getBytes());

        GPX gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(file.getInputStream());
        boolean hasPoints = gpx.tracks()
                .flatMap(Track::segments)
                .flatMap(TrackSegment::points)
                .findAny()
                .isPresent();

        assertThat(hasPoints).isFalse();
    }
}