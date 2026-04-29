package com.rideon.repository;

import com.rideon.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("rideon_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_shouldPersistUser() {
        // Arrange
        var user = new User();
        user.setEmail("davit@rideon.com");
        user.setPassword("hashedpassword");

        // Act
        var saved = userRepository.save(user);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("davit@rideon.com");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByEmail_shouldReturnUser_whenExists() {
        // Arrange
        var user = new User();
        user.setEmail("davit@rideon.com");
        user.setPassword("hashedpassword");
        userRepository.save(user);

        // Act
        var found = userRepository.findByEmail("davit@rideon.com");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("davit@rideon.com");
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenNotExists() {
        var found = userRepository.findByEmail("nobody@rideon.com");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_shouldReturnTrue_whenEmailTaken() {
        var user = new User();
        user.setEmail("davit@rideon.com");
        user.setPassword("hashedpassword");
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("davit@rideon.com")).isTrue();
    }

    @Test
    void existsByEmail_shouldReturnFalse_whenEmailFree() {
        assertThat(userRepository.existsByEmail("free@rideon.com")).isFalse();
    }

    @Test
    void save_shouldFail_whenEmailDuplicated() {
        var user1 = new User();
        user1.setEmail("davit@rideon.com");
        user1.setPassword("hashedpassword");
        userRepository.save(user1);

        var user2 = new User();
        user2.setEmail("davit@rideon.com");
        user2.setPassword("anotherpassword");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(Exception.class);
    }
}