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
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
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

    @Test
    void importGpx_createsRouteFromFile() throws Exception {
        byte[] gpxBytes = getClass().getResourceAsStream("/test-route.gpx").readAllBytes();

        mockMvc.perform(multipart("/api/v1/routes/import")
                        .file(new MockMultipartFile("file", "test-route.gpx",
                                "application/gpx+xml", gpxBytes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Tbilisi to Mtskheta"))
                .andExpect(jsonPath("$.coordinates").isArray())
                .andExpect(jsonPath("$.coordinates.length()").value(4));
    }

    @Test
    void importGpx_respectsVisibilityParam() throws Exception {
        byte[] gpxBytes = getClass().getResourceAsStream("/test-route.gpx").readAllBytes();

        mockMvc.perform(multipart("/api/v1/routes/import")
                        .file(new MockMultipartFile("file", "test-route.gpx",
                                "application/gpx+xml", gpxBytes))
                        .param("visibility", "private")
                        .param("description", "My private route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("private"))
                .andExpect(jsonPath("$.description").value("My private route"));
    }

    @Test
    void importGpx_preservesElevation() throws Exception {
        byte[] gpxBytes = getClass().getResourceAsStream("/test-route.gpx").readAllBytes();

        MvcResult result = mockMvc.perform(multipart("/api/v1/routes/import")
                        .file(new MockMultipartFile("file", "test-route.gpx",
                                "application/gpx+xml", gpxBytes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elevationGainM").isNumber())
                .andExpect(jsonPath("$.elevationLossM").isNumber())
                .andExpect(jsonPath("$.coordinates[0][2]").isNumber())
                .andExpect(jsonPath("$.coordinates[1][2]").isNumber())
                .andReturn();

        // test-route.gpx points: 490 -> 510 -> 540 -> 472 (all 4 survive simplification)
        // gain: (510-490) + (540-510) = 50m, loss: (540-472) = 68m
        String body = result.getResponse().getContentAsString();
        double gain = objectMapper.readTree(body).get("elevationGainM").asDouble();
        double loss = objectMapper.readTree(body).get("elevationLossM").asDouble();
        assertThat(gain).isEqualTo(50.0);
        assertThat(loss).isEqualTo(68.0);
    }

    @Test
    void createRoute_hasNullElevationInCoordinates() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("No Elevation Route", "public")))
                .andExpect(status().isCreated())
                // Manual routes: third coordinate element serializes as JSON null
                .andExpect(jsonPath("$.coordinates[0][2]").doesNotExist())
                .andExpect(jsonPath("$.elevationGainM").doesNotExist())
                .andExpect(jsonPath("$.elevationLossM").doesNotExist());
    }

    @Test
    void exportGpx_returnsGpxFile() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("Export Me", "public")))
                .andReturn();

        String routeId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/routes/" + routeId + "/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentType("application/gpx+xml"))
                .andExpect(content().string(containsString("<gpx")));
    }

    @Test
    void updateRoute_updatesTitleAndVisibility() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("Original Title", "public")))
                .andReturn();

        String routeId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        String patch = """
                { "title": "Updated Title", "visibility": "private" }
                """;

        mockMvc.perform(patch("/api/v1/routes/" + routeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.visibility").value("private"))
                // description not in patch body → unchanged
                .andExpect(jsonPath("$.description").value("A scenic route"));
    }

    @Test
    void updateRoute_returnsNotFound_forAnotherUsersRoute() throws Exception {
        // Route owned by the @BeforeEach user
        MvcResult created = mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("Someone Elses", "public")))
                .andReturn();

        String routeId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // A second, different user
        var otherReg = new RegisterRequest("other" + UUID.randomUUID() + "@example.com", "password123");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(otherReg)));
        MvcResult otherLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(otherReg.email(), "password123"))))
                .andReturn();
        String otherToken = objectMapper.readTree(otherLogin.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(patch("/api/v1/routes/" + routeId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Hijacked\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRoute_returnsBadRequest_forInvalidVisibility() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("Valid Route", "public")))
                .andReturn();

        String routeId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/routes/" + routeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"visibility\": \"secret\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRoutesByUser_returnsOnlyPublicRoutes_andIsPublic() throws Exception {
        // Owner creates one public + one private route
        mockMvc.perform(post("/api/v1/routes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(routeBody("Public One", "public")));
        mockMvc.perform(post("/api/v1/routes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(routeBody("Private One", "private")));

        // Resolve the owner's userId from one of their routes via /my
        MvcResult my = mockMvc.perform(get("/api/v1/routes/my")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        String userId = objectMapper.readTree(my.getResponse().getContentAsString())
                .get(0).get("userId").asText();

        // No token — endpoint is public discovery
        mockMvc.perform(get("/api/v1/routes").param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.visibility == 'private')]").isEmpty())
                .andExpect(jsonPath("$[?(@.title == 'Public One')]").exists());
    }
}