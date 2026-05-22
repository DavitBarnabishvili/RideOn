package com.rideon.controller;

import com.rideon.dto.request.RouteRequest;
import com.rideon.dto.response.RouteResponse;
import com.rideon.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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