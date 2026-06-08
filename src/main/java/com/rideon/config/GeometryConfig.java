package com.rideon.config;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeometryConfig {

    /**
     * Shared GeometryFactory using WGS84 (SRID 4326).
     * GeometryFactory is stateless and thread-safe — one instance shared across
     * all services. SRID 4326 matches GPS coordinates and PostGIS convention.
     */
    @Bean
    public GeometryFactory geometryFactory() {
        return new GeometryFactory(new PrecisionModel(), 4326);
    }
}
