package com.rideon.repository;

import com.rideon.domain.Bike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BikeRepository extends JpaRepository<Bike, UUID> {

    List<Bike> findByUserId(UUID userId);

    Optional<Bike> findByIdAndUserId(UUID id, UUID userId);
}