package com.goal_tracker.config.security;

import tools.jackson.databind.ObjectMapper;
import com.goal_tracker.AbstractIntegrationTest;
import com.goal_tracker.auth.dto.LoginRequest;
import com.goal_tracker.auth.dto.PasswordUpdateRequest;
import com.goal_tracker.auth.dto.RegisterRequest;
import com.goal_tracker.config.security.details.UserPrincipal;
import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.entity.GoalStatusHistory;
import com.goal_tracker.goals.entity.StatusTransitionCauseEnum;
import com.goal_tracker.goals.repository.GoalRepository;
import com.goal_tracker.goals.repository.GoalStatusHistoryRepository;
import com.goal_tracker.users.entity.User;
import com.goal_tracker.users.repository.UserRepository;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;

/**
 * Integration tests for SecurityConfig.
 * 
 * This class tests the security configuration of the application, focusing on public endpoints and CORS behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Log4j2 
class SecurityConfigIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired 
    private GoalRepository goalRepository;

    @Autowired 
    private  GoalStatusHistoryRepository goalStatusHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;


    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        SecurityContextHolder.clearContext();
    }

    /**
     * Test to verify that the Actuator Health endpoint is accessible without authentication.
     * This test sends a GET request to the /actuator/health endpoint and expects a 200 OK response.
     */
    @Test
    @DisplayName("Security Config - Should allow access to Actuator Health without authentication")
    void shouldAllowAccessToActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    log.info("Response status: {}", status);
                    assertEquals(200, status);
                });
    }

    /**
     * Test to verify that requests to the Actuator Health endpoint are blocked by CORS when the origin is not allowed.
     * This test sends a GET request to the /actuator/health endpoint with a disallowed origin and expects a 403 Forbidden response.
     */
    @Test
    @DisplayName("Security Config - Should be blocked by CORS when origin not allowed")
    void shouldCorsShouldBlockRequest() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:9000"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    log.info("Response status: {}", status);
                    assertEquals(403, status);
                });
    }

    /**
     * Test to verify that the Actuator Health endpoint returns a 404 Not Found for non-existent sub-endpoints.
     * This test sends a GET request to a non-existent sub-endpoint of /actuator/health and expects a 404 Not Found response.
     */
    @Test
    @DisplayName("Security Config - Should return 404 for non-existent actuatorendpoint")
    void should404WhenAccessingNonExistentActuatorEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health/non-existent-endpoint")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    log.info("Response status: {}", status);
                    assertEquals(404, status);
                });
    }
    
    /**
     * Tests that public authentication endpoints (register, login, guest, password)
     * are accessible without requiring prior HTTP basic or bearer token authentication.
     */
    @Test
    @DisplayName("Security Config - Should allow unauthenticated access to /auth/register")
    void shouldAllowUnauthenticatedAccessToRegister() throws Exception {
        RegisterRequest request = new RegisterRequest("test.security@example.com", "SecurePass123!", "SecurePass123!");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Security Config - Should allow unauthenticated access to /auth/login")
    void shouldAllowUnauthenticatedAccessToLogin() throws Exception {
        String email = "login.security@example.com";
        String rawPassword = "SecurePass123!";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsGuest(false);
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        userRepository.save(user);
        userRepository.flush();

        LoginRequest request = new LoginRequest(email, rawPassword);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security Config - Should allow unauthenticated access to /auth/guest")
    void shouldAllowUnauthenticatedAccessToGuest() throws Exception {
        mockMvc.perform(post("/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Security Config - Should allow authenticated access to /auth/password")
    void shouldAllowAuthenticatedAccessToUpdatePassword() throws Exception {
        
        String email = "login.security@example.com";
        String rawPassword = "CurrentPass123!";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsGuest(false);
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        userRepository.save(user);
        userRepository.flush();

        var userPrincipal = new UserPrincipal(user.getId(), email);
        var auth = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, java.util.Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();
        PasswordUpdateRequest request = new PasswordUpdateRequest("CurrentPass123!", "NewSecurePass123!", "NewSecurePass123!");

        mockMvc.perform(patch("/auth/password")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Security Config - Should denny authenticated access to /auth/password")
    void shouldDennyUnauthenticatedAccessToUpdatePassword() throws Exception {

        PasswordUpdateRequest request = new PasswordUpdateRequest("CurrentPass123!", "NewSecurePass123!", "NewSecurePass123!");

        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

    }

    @Test
    @DisplayName("Security Config - Should allow authenticated access to /goals/roots")
    void shouldAllowAuthenticatedAccessToListRoots() throws Exception {
        
        String email = "login.security@example.com";
        String rawPassword = "CurrentPass123!";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsGuest(false);
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        userRepository.save(user);
        userRepository.flush();

        var userPrincipal = new UserPrincipal(user.getId(), email);
        var auth = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, java.util.Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        SecurityContext context = SecurityContextHolder.getContext();
        mockMvc.perform(get("/goals/roots")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Security Config - Should denny unauthenticated access to /goals/roots")
    void shouldDennyUnauthenticatedAccessToListRoots() throws Exception {
        mockMvc.perform(get("/goals/roots")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }


    @Test
    @DisplayName("Security Config - Should allow authenticated access to /goals/{id}/tree")
    void shouldAllowAuthenticatedAccessToGoalTree() throws Exception {
        
        String email = "login.security@example.com";
        String rawPassword = "CurrentPass123!";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsGuest(false);
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        userRepository.saveAndFlush(user);

        Goal root = new Goal(
            user.getId(),
            null,
            "Root tittle",
            "Desc",
            LocalDate.now().minusDays(2),
            LocalDate.now().plusDays(1),
            GoalStatusEnum.WAITING
        );

        root = goalRepository.saveAndFlush(root);

        goalStatusHistoryRepository.saveAndFlush(
            new GoalStatusHistory(
                root,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(3)
            )
        );


        var userPrincipal = new UserPrincipal(user.getId(), email);
        var auth = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, java.util.Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        SecurityContext context = SecurityContextHolder.getContext();
        mockMvc.perform(get("/goals/{id}/tree", root.getId())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(root.getId()))
                .andExpect(jsonPath("$.title").value(root.getTitle()))
                .andExpect(jsonPath("$.subGoals").isArray())
                .andExpect(jsonPath("$.subGoals").isEmpty());
    }

    @Test
    @DisplayName("Security Config - Should denny unauthenticated access to /goals/roots")
    void shouldDennyUnauthenticatedAccessToToGoalTree() throws Exception {
        
        String email = "login.security@example.com";
        String rawPassword = "CurrentPass123!";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsGuest(false);
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        userRepository.saveAndFlush(user);

        Goal root = new Goal(
            user.getId(),
            null,
            "Root tittle",
            "Desc",
            LocalDate.now().minusDays(2),
            LocalDate.now().plusDays(1),
            GoalStatusEnum.WAITING
        );

        root = goalRepository.saveAndFlush(root);

        goalStatusHistoryRepository.saveAndFlush(
            new GoalStatusHistory(
                root,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(3)
            )
        );

        mockMvc.perform(get("/goals/{id}/tree", root.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Test to verify that an authenticated user can successfully create a root goal via POST /goals.
     * <p>
     * Sets up a valid user, injects authentication context, performs a POST request with a valid goal payload,
     * and expects a 201 Created response along with the generated goal details.
     * </p>
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("Security Config - Should allow authenticated access to create root goal via POST /goals")
    void shouldAllowAuthenticatedAccessToCreateRootGoal() throws Exception {
        String email = "login.security@example.com";
        String rawPassword = "CurrentPass123!";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsGuest(false);
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        userRepository.saveAndFlush(user);

        var userPrincipal = new UserPrincipal(user.getId(), email);
        var auth = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, java.util.Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalCreateRequestDto requestDto = new GoalCreateRequestDto(
            "New Integration Root Goal",
            "Integration test description",
            LocalDate.now(),
            LocalDate.now().plusDays(10),
            null
        );

        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("New Integration Root Goal"))
                .andExpect(jsonPath("$.parentId").isEmpty());
    }

    /**
     * Test to verify that unauthenticated requests to create a root goal via POST /goals are denied.
     * <p>
     * Sends a POST request without any security context or bearer token, expecting a 401 Unauthorized response
     * along with the standard error structure.
     * </p>
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("Security Config - Should deny unauthenticated access to create root goal via POST /goals")
    void shouldDennyUnauthenticatedAccessToCreateRootGoal() throws Exception {
        GoalCreateRequestDto requestDto = new GoalCreateRequestDto(
            "New Integration Root Goal",
            "Integration test description",
            LocalDate.now(),
            LocalDate.now().plusDays(10),
            null
        );

        mockMvc.perform(post("/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }
}