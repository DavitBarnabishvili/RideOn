package com.rideon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideon.dto.request.LoginRequest;
import com.rideon.dto.request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.servlet.multipart.max-file-size=1KB")
@Testcontainers
@ActiveProfiles("test")
class RouteImportLimitIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
    ).withInitScript("postgis-init.sql");

    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        var register = new RegisterRequest(
                "rider" + UUID.randomUUID() + "@example.com", "password123");

        restTemplate.postForEntity("/api/v1/auth/register", register, String.class);

        ResponseEntity<String> login = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(register.email(), "password123"),
                String.class);

        token = objectMapper.readTree(login.getBody()).get("token").asText();
    }

    @Test
    void importGpx_overSizeLimit_returnsPayloadTooLarge() {
        byte[] oversized = new byte[2048];
        Arrays.fill(oversized, (byte) 'a');

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.valueOf("application/gpx+xml"));
        fileHeaders.setContentDispositionFormData("file", "huge.gpx");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(oversized, fileHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/routes/import", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
