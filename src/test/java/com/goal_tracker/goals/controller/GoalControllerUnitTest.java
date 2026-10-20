package com.goal_tracker.goals.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.goal_tracker.config.security.details.UserPrincipal;
import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.GlobalExceptionHandler;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.exceptions.GoalStatusNotFoundException;
import com.goal_tracker.goals.service.GoalService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Unit tests for {@link GoalController}.
 * <p>
 * Focuses on testing the web layer independently by mocking the underlying {@link GoalService}.
 * Validates request mapping, query parameter parsing, security principal extraction,
 * HTTP status code responses, and JSON payload formatting using {@link MockMvc}.
 * </p>
 */
class GoalControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoalService goalService;

    private GoalController goalController;

    private static final Long TEST_USER_ID = 1L;

    /**
     * Sets up the test environment before each test execution.
     * <p>
     * Initializes mocks, instantiates the target controller, configures 
     * {@link MockMvc} in standalone mode with custom argument resolvers for {@link UserPrincipal},
     * and sets up exception handling.
     * </p>
     */
    @BeforeEach
    void setUp() {
        goalService = Mockito.mock(GoalService.class);
        goalController = new GoalController(goalService);
        
        mockMvc = MockMvcBuilders.standaloneSetup(goalController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        objectMapper = JsonMapper.builder()
            .withCoercionConfig(LogicalType.Integer, config -> 
                config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            )
            .build();
        SecurityContextHolder.clearContext();
    }

    /**
     * Tests successful retrieval of root goals via GET /api/goals/roots.
     * Expects 200 OK and a JSON array of goals.
     */
    @Test
    @DisplayName("GET /goals/roots - Should return root goals successfully when user is authenticated")
    void getRootGoalsShouldReturnGoalsSuccessfully() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        List<GoalResponseDto> responseDtos = List.of(
            new GoalResponseDto
            (
                1L, 
                "Root Goal 1", 
                "Description 1", 
                LocalDate.now(), 
                LocalDate.now().plusDays(10),
                GoalStatusEnum.WAITING.getId(),
                null,
                OffsetDateTime.now(),
                null
            )
        );

        when(goalService.getRootGoals(eq(TEST_USER_ID), isNull())).thenReturn(responseDtos);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
        // Act & Assert
        mockMvc.perform(get("/goals/roots")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].title").value("Root Goal 1"))
                .andExpect(jsonPath("$[0].statusId").value(GoalStatusEnum.WAITING.getId()));

        verify(goalService).getRootGoals(TEST_USER_ID, null);
    }

    /**
     * Tests GET /api/goals/roots when the user has no root goals, expecting an empty array.
     */
    @Test
    @DisplayName("GET /goals/roots - Should return empty list when no root goals exist")
    void getRootGoalsShouldReturnEmptyListWhenNoGoals() throws Exception {
        // Arrange
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        
        when(goalService.getRootGoals(eq(TEST_USER_ID), isNull())).thenReturn(Collections.emptyList());
        
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
        // Act & Assert
        mockMvc.perform(get("/goals/roots")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * Tests GET /goals/roots with a specific status filter (e.g., status_id = 2 for IN_PROGRESS).
     */
    @Test
    @DisplayName("GET //goals/roots - Should filter root goals by status identifier successfully")
    void getRootGoalsShouldFilterByStatusSuccessfully() throws Exception {
        // Arrange
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        List<GoalResponseDto> responseDtos = List.of(
            new GoalResponseDto
            (
                1L, 
                "Root Goal 1", 
                "Description 1", 
                LocalDate.now(), 
                LocalDate.now().plusDays(10),
                GoalStatusEnum.IN_PROGRESS.getId(),
                null,
                OffsetDateTime.now(),
                null
            )
        );

        when(goalService.getRootGoals(eq(TEST_USER_ID), eq(GoalStatusEnum.IN_PROGRESS.getId()))).thenReturn(responseDtos);
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
        // Act & Assert
        mockMvc.perform(get("/goals/roots")
                        .with(securityContext(context))
                        .param("status_id", String.valueOf(GoalStatusEnum.IN_PROGRESS.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].statusId").value(GoalStatusEnum.IN_PROGRESS.getId()));

        verify(goalService).getRootGoals(TEST_USER_ID, GoalStatusEnum.IN_PROGRESS.getId());
    }

    /**
     * Tests GET /goals/roots failure when an invalid status identifier is provided,
     * expecting 400 Bad Request via GoalStatusNotFoundException handling.
     */
    @Test
    @DisplayName("GET /goals/roots - Should return 400 Bad Request when status identifier does not exist")
    void getRootGoalsShouldReturn400WhenStatusIdIsInvalid() throws Exception {
        // Arrange
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        
        Integer invalidStatusId = 999;

        when(goalService.getRootGoals(eq(TEST_USER_ID), eq(invalidStatusId)))
                .thenThrow(new GoalStatusNotFoundException(invalidStatusId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
        // Act & Assert
        mockMvc.perform(get("/goals/roots")
                        .with(securityContext(context))
                        .param("status_id", String.valueOf(invalidStatusId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_GOAL_STATUS_NOT_FOUND.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests GET /goals/roots failure when status_id query parameter has a type mismatch (non-integer).
     */
    @Test
    @DisplayName("GET /goals/roots - Should return 400 Bad Request when status_id has type mismatch")
    void getRootGoalsShouldReturn400WhenStatusIdTypeMismatch() throws Exception {
        // Arrange
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
        // Act & Assert
        mockMvc.perform(get("/goals/roots")
                        .with(securityContext(context))
                        .param("status_id", "invalid-string")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }
}