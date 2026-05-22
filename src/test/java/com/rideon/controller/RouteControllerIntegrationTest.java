package com.rideon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideon.dto.request.LoginRequest;
import com.rideon.dto.request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RouteControllerIntegrationTest {

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
        var register = new RegisterRequest(
                "rider" + UUID.randomUUID() + "@example.com", "password123");

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

    private String routeBody(String title, String visibility) {
        return """
                {
                  "title": "%s",
                  "description": "A scenic route",
                  "coordinates": [[44.79, 41.69], [44.80, 41.70], [44.82, 41.72]],
                  "visibility": "%s"
                }
                """.formatted(title, visibility);
    }

    @Test
    void createRoute_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("Mountain Pass", "public")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Mountain Pass"))
                .andExpect(jsonPath("$.visibility").value("public"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.coordinates").isArray());
    }

    @Test
    void getMyRoutes_returnsRoutesAfterCreating() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(routeBody("Coastal Run", "private")));

        mockMvc.perform(get("/api/v1/routes/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Coastal Run"));
    }

    @Test
    void getRoutesNear_returnsNearbyPublicRoutes() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(routeBody("Tbilisi Loop", "public")));

        mockMvc.perform(get("/api/v1/routes/near")
                        .param("lat", "41.69")
                        .param("lon", "44.79")
                        .param("radiusMeters", "50000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Tbilisi Loop"));
    }

    @Test
    void getRoutesNear_isPublic_noTokenRequired() throws Exception {
        mockMvc.perform(get("/api/v1/routes/near")
                        .param("lat", "41.69")
                        .param("lon", "44.79"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRoute_returnsNoContent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("Delete Me", "public")))
                .andReturn();

        String routeId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/api/v1/routes/" + routeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void createRoute_returnsUnauthorized_withNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("Unauthorized", "public")))
                .andExpect(status().isUnauthorized());
    }
}