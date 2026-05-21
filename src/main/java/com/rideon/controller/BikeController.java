package com.rideon.controller;

import com.rideon.dto.request.BikeRequest;
import com.rideon.dto.response.BikeResponse;
import com.rideon.service.BikeService;
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
@RequestMapping("${application.api.prefix}/bikes")
@RequiredArgsConstructor
public class BikeController {

    private final BikeService bikeService;

    @PostMapping
    public ResponseEntity<BikeResponse> addBike(
            @Valid @RequestBody BikeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bikeService.addBike(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<BikeResponse>> getMyBikes(Authentication authentication) {
        return ResponseEntity.ok(bikeService.getBikesForUser(authentication.getName()));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BikeResponse> uploadPhoto(
            @PathVariable UUID id,
            @RequestPart MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(bikeService.uploadPhoto(authentication.getName(), id, file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBike(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        bikeService.deleteBike(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}