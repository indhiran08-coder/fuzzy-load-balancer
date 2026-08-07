package com.fuzzybalancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuzzybalancer.auth.dto.LoginRequest;
import com.fuzzybalancer.auth.dto.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * AuthIntegrationTest — Full Spring Boot integration tests for the Auth module.
 *
 * @SpringBootTest — Loads the FULL Spring application context.
 *   This is a true integration test — it tests the entire stack:
 *   Controller → Service → Repository → Database (H2 in-memory for tests)
 *
 * @AutoConfigureMockMvc — Configures MockMvc automatically.
 *   MockMvc lets us send HTTP requests to controllers without a real HTTP server.
 *   Faster than @SpringBootTest(webEnvironment = RANDOM_PORT).
 *
 * @ActiveProfiles("test") — Activates the 'test' profile.
 *   The test profile overrides datasource to H2 in-memory DB.
 *   This isolates tests from the development MySQL database.
 *
 * @TestMethodOrder — Ensures tests run in the order defined by @Order annotations.
 *   Login must run AFTER register, so we need ordered execution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auth Integration Tests")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Shared JWT token across tests — set during login test, used in protected tests. */
    private static String jwtToken;

    // =========================================================================
    // REGISTER TESTS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/register — Success: new user created")
    void register_validRequest_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.username").value("testuser"))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/register — Fail: duplicate username returns 409")
    void register_duplicateUsername_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser"); // Already registered in test 1
        request.setEmail("another@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/auth/register — Fail: blank username returns 400")
    void register_blankUsername_returns400WithValidationError() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(""); // Blank
        request.setEmail("valid@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data.username").exists()); // Field error
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/register — Fail: password mismatch returns 400")
    void register_passwordMismatch_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser2");
        request.setEmail("new2@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("different456"); // Mismatch

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // LOGIN TESTS
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("POST /api/auth/login — Success: returns JWT token")
    void login_validCredentials_returnsToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("admin"); // Seeded in DataInitializer
        request.setPassword("admin123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.roles", hasItem("ROLE_ADMIN")))
            .andReturn();

        // Extract token for use in subsequent tests
        String responseBody = result.getResponse().getContentAsString();
        jwtToken = objectMapper.readTree(responseBody)
            .get("data").get("accessToken").asText();
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/auth/login — Fail: wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("admin");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/auth/login — Fail: non-existent user returns 401")
    void login_nonExistentUser_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("ghost_user_xyz");
        request.setPassword("somepassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    // =========================================================================
    // JWT PROTECTION TESTS
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("GET /api/servers — No token returns 403")
    void protectedEndpoint_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/servers"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    @DisplayName("GET /api/servers — Valid JWT token returns 200")
    void protectedEndpoint_validToken_returns200() throws Exception {
        // Ensure we have a token from the login test
        Assumptions.assumeTrue(jwtToken != null, "JWT token must be set from login test");

        mockMvc.perform(get("/api/servers")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/servers — Invalid JWT token returns 403")
    void protectedEndpoint_invalidToken_returns403() throws Exception {
        mockMvc.perform(get("/api/servers")
                .header("Authorization", "Bearer invalid.jwt.token"))
            .andExpect(status().isForbidden());
    }
}
