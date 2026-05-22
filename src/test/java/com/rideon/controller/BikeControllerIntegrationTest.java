package com.rideon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideon.dto.request.LoginRequest;
import com.rideon.dto.request.RegisterRequest;
import com.rideon.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BikeControllerIntegrationTest {

    @MockitoBean
    private FileStorageService fileStorageService;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
            ).withInitScript("postgis-init.sql");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        when(fileStorageService.store(any(), any())).thenReturn("https://cloudinary.com/test-photo.jpg");
        // register and login before each test to get a fresh token
        var register = new RegisterRequest(
                "biker" + UUID.randomUUID() + "@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(register.email(), "password123"))))
                .andReturn();

        token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("token").asText();
    }

    @Test
    void addBike_returnsCreated() throws Exception {
        String body = """
                {
                  "make": "Honda",
                  "model": "CB500F",
                  "year": 2021,
                  "engineCc": 500,
                  "type": "naked",
                  "nickname": "my honda"
                }
                """;

        mockMvc.perform(post("/api/v1/bikes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.make").value("Honda"))
                .andExpect(jsonPath("$.model").value("CB500F"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void getMyBikes_returnsListAfterAdding() throws Exception {
        String body = """
                {
                  "make": "Yamaha",
                  "model": "MT-07",
                  "year": 2022,
                  "engineCc": 689
                }
                """;

        mockMvc.perform(post("/api/v1/bikes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(get("/api/v1/bikes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].make").value("Yamaha"));
    }

    @Test
    void deleteBike_returnsNoContent() throws Exception {
        String body = """
                {
                  "make": "Kawasaki",
                  "model": "Z650",
                  "year": 2020,
                  "engineCc": 649
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/bikes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        String bikeId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/api/v1/bikes/" + bikeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void addBike_returnsUnauthorized_withNoToken() throws Exception {
        String body = """
                {
                  "make": "Honda",
                  "model": "CB500F",
                  "year": 2021,
                  "engineCc": 500
                }
                """;

        mockMvc.perform(post("/api/v1/bikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadPhoto_returnsOk() throws Exception {
        String body = """
                {
                  "make": "Ducati",
                  "model": "Monster",
                  "year": 2023,
                  "engineCc": 937
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/bikes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        String bikeId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        MockMultipartFile photo = new MockMultipartFile(
                "file", "bike.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/v1/bikes/" + bikeId + "/photo")
                        .file(photo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}