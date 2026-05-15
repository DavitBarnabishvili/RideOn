package com.rideon.service;

import com.rideon.domain.User;
import com.rideon.dto.request.RegisterRequest;
import com.rideon.dto.response.UserResponse;
import com.rideon.exception.EmailAlreadyExistsException;
import com.rideon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getCreatedAt());
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .authorities("ROLE_USER") // no role system yet; revisit if admin roles are needed
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}