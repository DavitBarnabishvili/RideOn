package com.rideon.service;

import com.rideon.domain.Bike;
import com.rideon.domain.User;
import com.rideon.dto.request.BikeRequest;
import com.rideon.dto.response.BikeResponse;
import com.rideon.exception.BikeNotFoundException;
import com.rideon.repository.BikeRepository;
import com.rideon.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BikeServiceTest {

    @Mock private BikeRepository bikeRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private BikeService bikeService;

    private User user;
    private Bike bike;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("rider@example.com");

        bike = new Bike();
        bike.setUser(user);
        bike.setMake("KTM");
        bike.setModel("DUKE");
        bike.setYear(2019);
        bike.setEngineCc(200);
    }

    // --- addBike ---

    @Test
    void addBike_returnsBikeResponse_whenUserExists() {
        var request = new BikeRequest("KTM", "DUKE", 2019, 200, "naked", "Ginger");

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(bikeRepository.save(any(Bike.class))).thenReturn(bike);

        BikeResponse response = bikeService.addBike("rider@example.com", request);

        assertThat(response.make()).isEqualTo("KTM");
        assertThat(response.model()).isEqualTo("DUKE");
        verify(bikeRepository).save(any(Bike.class));
    }

    @Test
    void addBike_throwsUsernameNotFoundException_whenUserNotFound() {
        var request = new BikeRequest("KTM", "DUKE", 2019, 200, null, null);

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bikeService.addBike("ghost@example.com", request))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(bikeRepository, never()).save(any());
    }

    // --- getBikesForUser ---

    @Test
    void getBikesForUser_returnsListOfBikes() {
        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(bikeRepository.findByUserId(any())).thenReturn(List.of(bike));

        List<BikeResponse> responses = bikeService.getBikesForUser("rider@example.com");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().make()).isEqualTo("KTM");
    }

    // --- uploadPhoto ---

    @Test
    void uploadPhoto_updatesPhotoUrl_whenBikeOwnedByUser() {
        UUID bikeId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(bikeRepository.findByIdAndUserId(bikeId, user.getId())).thenReturn(Optional.of(bike));
        when(fileStorageService.store(file, "bikes")).thenReturn("https://cloudinary.com/photo.jpg");
        when(bikeRepository.save(any(Bike.class))).thenReturn(bike);

        BikeResponse response = bikeService.uploadPhoto("rider@example.com", bikeId, file);

        verify(fileStorageService).store(file, "bikes");
        verify(bikeRepository).save(bike);
    }

    @Test
    void uploadPhoto_throwsBikeNotFoundException_whenBikeNotOwnedByUser() {
        UUID bikeId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(bikeRepository.findByIdAndUserId(bikeId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bikeService.uploadPhoto("rider@example.com", bikeId, file))
                .isInstanceOf(BikeNotFoundException.class);

        verify(fileStorageService, never()).store(any(), any());
    }

    // --- deleteBike ---

    @Test
    void deleteBike_deletesBike_whenOwnedByUser() {
        UUID bikeId = UUID.randomUUID();

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(bikeRepository.findByIdAndUserId(bikeId, user.getId())).thenReturn(Optional.of(bike));

        bikeService.deleteBike("rider@example.com", bikeId);

        verify(bikeRepository).delete(bike);
    }

    @Test
    void deleteBike_throwsBikeNotFoundException_whenBikeNotOwnedByUser() {
        UUID bikeId = UUID.randomUUID();

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));
        when(bikeRepository.findByIdAndUserId(bikeId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bikeService.deleteBike("rider@example.com", bikeId))
                .isInstanceOf(BikeNotFoundException.class);

        verify(bikeRepository, never()).delete(any());
    }
}