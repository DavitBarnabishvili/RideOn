package com.rideon.service;

import com.rideon.domain.Bike;
import com.rideon.domain.User;
import com.rideon.dto.request.BikeRequest;
import com.rideon.dto.response.BikeResponse;
import com.rideon.exception.BikeNotFoundException;
import com.rideon.repository.BikeRepository;
import com.rideon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BikeService {

    private final BikeRepository bikeRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public BikeResponse addBike(String email, BikeRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        Bike bike = new Bike();
        bike.setUser(user);
        bike.setMake(request.make());
        bike.setModel(request.model());
        bike.setYear(request.year());
        bike.setEngineCc(request.engineCc());
        bike.setType(request.type());
        bike.setNickname(request.nickname());

        return toResponse(bikeRepository.save(bike));
    }

    public List<BikeResponse> getBikesForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return bikeRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public BikeResponse uploadPhoto(String email, UUID bikeId, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        Bike bike = bikeRepository.findByIdAndUserId(bikeId, user.getId())
                .orElseThrow(() -> new BikeNotFoundException("Bike not found: " + bikeId));

        String url = fileStorageService.store(file, "bikes");
        bike.setPhotoUrl(url);

        return toResponse(bikeRepository.save(bike));
    }

    public void deleteBike(String email, UUID bikeId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        Bike bike = bikeRepository.findByIdAndUserId(bikeId, user.getId())
                .orElseThrow(() -> new BikeNotFoundException("Bike not found: " + bikeId));

        bikeRepository.delete(bike);
    }

    private BikeResponse toResponse(Bike bike) {
        return new BikeResponse(
                bike.getId(),
                bike.getMake(),
                bike.getModel(),
                bike.getYear(),
                bike.getEngineCc(),
                bike.getType(),
                bike.getNickname(),
                bike.getPhotoUrl(),
                bike.getCreatedAt()
        );
    }
}