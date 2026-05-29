package com.rideon.controller;

import com.rideon.dto.request.RouteRequest;
import com.rideon.dto.response.RouteResponse;
import com.rideon.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${application.api.prefix}/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    public ResponseEntity<RouteResponse> createRoute(
            @Valid @RequestBody RouteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(routeService.createRoute(authentication.getName(), request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RouteResponse> importGpx(
            @RequestPart MultipartFile file,
            @RequestParam(defaultValue = "public") String visibility,
            @RequestParam(required = false) String description,
            Authentication authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(routeService.importGpx(authentication.getName(), file, visibility, description));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportGpx(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        byte[] gpxBytes = routeService.exportGpx(authentication.getName(), id);
        String filename = "route-" + id + ".gpx";
        return ResponseEntity.ok()
                .header("Content-Type", "application/gpx+xml")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(gpxBytes);
    }

    @GetMapping("/my")
    public ResponseEntity<List<RouteResponse>> getMyRoutes(Authentication authentication) {
        return ResponseEntity.ok(routeService.getMyRoutes(authentication.getName()));
    }

    @GetMapping("/near")
    public ResponseEntity<List<RouteResponse>> getRoutesNear(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "50000") double radiusMeters,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(routeService.getRoutesNear(lat, lon, radiusMeters, limit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoute(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        routeService.deleteRoute(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}