package com.rideon.service;

import com.rideon.domain.User;
import com.rideon.dto.request.RegisterRequest;
import com.rideon.dto.response.UserResponse;
import com.rideon.exception.EmailAlreadyExistsException;
import com.rideon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // --- register ---

    @Test
    void register_savesUserAndReturnsResponse_whenEmailIsUnique() {
        var request = new RegisterRequest("rider@example.com", "password123");

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");

        User saved = new User();
        saved.setEmail(request.email());
        saved.setPassword("hashed");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse response = userService.register(request);

        assertThat(response.email()).isEqualTo("rider@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsEmailAlreadyExistsException_whenEmailIsTaken() {
        var request = new RegisterRequest("taken@example.com", "password123");

        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("taken@example.com");

        verify(userRepository, never()).save(any());
    }

    // --- loadUserByUsername ---

    @Test
    void loadUserByUsername_throwsUsernameNotFoundException_whenEmailNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_returnsUserDetails_whenEmailExists() {
        User user = new User();
        user.setEmail("rider@example.com");
        user.setPassword("hashed_password");

        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("rider@example.com");

        assertThat(details.getUsername()).isEqualTo("rider@example.com");
        assertThat(details.getPassword()).isEqualTo("hashed_password");
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }
}