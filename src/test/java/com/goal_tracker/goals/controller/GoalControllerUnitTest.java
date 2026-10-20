package com.goal_tracker.goals.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.dto.GoalTreeResponseDto;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.exceptions.GoalEndDateViolationException;
import com.goal_tracker.goals.exceptions.GoalNotFoundException;
import com.goal_tracker.goals.exceptions.GoalOwnershipException;
import com.goal_tracker.goals.exceptions.GoalParentNotFoundException;
import com.goal_tracker.goals.exceptions.GoalStartDateViolationException;
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


    @MockitoBean
    private GoalService goalService;

    private GoalController goalController;

    private ObjectMapper objectMapper;


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

    /**
     * Tests successful retrieval of a single goal tree structure with empty sub-goals.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("GET /goals/{id}/tree - Should return single goal tree with empty sub-goals successfully")
    void getGoalTreeShouldReturnSingleGoalTreeSuccessfully() throws Exception {
        Long goalId = 10L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        GoalTreeResponseDto treeDto = new GoalTreeResponseDto(
            goalId,
            "Standalone Goal",
            GoalStatusEnum.IN_PROGRESS.getId(),
            LocalDate.now(),
            LocalDate.now().plusDays(5),
            Collections.emptyList()
        );

        when(goalService.getGoalTree(TEST_USER_ID, goalId)).thenReturn(treeDto);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(get("/goals/{id}/tree", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(goalId))
                .andExpect(jsonPath("$.title").value("Standalone Goal"))
                .andExpect(jsonPath("$.subGoals").isArray())
                .andExpect(jsonPath("$.subGoals").isEmpty());

        verify(goalService).getGoalTree(TEST_USER_ID, goalId);
    }

    /**
     * Tests successful retrieval of a populated goal tree including nested sub-goals.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("GET /goals/{id}/tree - Should return populated goal tree with nested sub-goals successfully")
    void getGoalTreeShouldReturnNestedGoalTreeSuccessfully() throws Exception {
        Long rootId = 10L;
        Long childId = 20L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        GoalTreeResponseDto childDto = new GoalTreeResponseDto(
            childId,
            "Child Sub-Goal",
            GoalStatusEnum.WAITING.getId(),
            LocalDate.now(),
            LocalDate.now().plusDays(3),
            Collections.emptyList()
        );

        GoalTreeResponseDto rootTreeDto = new GoalTreeResponseDto(
            rootId,
            "Parent Goal",
            GoalStatusEnum.IN_PROGRESS.getId(),
            LocalDate.now(),
            LocalDate.now().plusDays(10),
            List.of(childDto)
        );

        when(goalService.getGoalTree(TEST_USER_ID, rootId)).thenReturn(rootTreeDto);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(get("/goals/{id}/tree", rootId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(rootId))
                .andExpect(jsonPath("$.title").value("Parent Goal"))
                .andExpect(jsonPath("$.subGoals").isArray())
                .andExpect(jsonPath("$.subGoals[0].id").value(childId))
                .andExpect(jsonPath("$.subGoals[0].title").value("Child Sub-Goal"));

        verify(goalService).getGoalTree(TEST_USER_ID, rootId);
    }

    /**
     * Tests response handling when requesting a goal tree for a non-existent goal, expecting a 404 Not Found status.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("GET /goals/{id}/tree - Should return 404 Not Found when goal does not exist")
    void getGoalTreeShouldReturn404WhenGoalDoesNotExist() throws Exception {
        Long nonexistentId = 99L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.getGoalTree(TEST_USER_ID, nonexistentId))
                .thenThrow(new GoalNotFoundException(nonexistentId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(get("/goals/{id}/tree", nonexistentId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_NOT_FOUND.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).getGoalTree(TEST_USER_ID, nonexistentId);
    }

    /**
     * Tests response handling when requesting a goal tree belonging to another user, expecting a 403 Forbidden status.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("GET /goals/{id}/tree - Should return 403 Forbidden when goal belongs to another user")
    void getGoalTreeShouldReturn403WhenGoalNotOwned() throws Exception {
        Long otherUserGoalId = 50L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.getGoalTree(TEST_USER_ID, otherUserGoalId))
                .thenThrow(new GoalOwnershipException(otherUserGoalId, TEST_USER_ID));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(get("/goals/{id}/tree", otherUserGoalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_FORBIDDEN.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).getGoalTree(TEST_USER_ID, otherUserGoalId);
    }

    /**
     * Tests successful creation of a root-level goal (without parentId).
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should create root goal successfully when payload is valid")
    void createGoalShouldCreateRootGoalSuccessfully() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        GoalResponseDto responseDto = new GoalResponseDto(
            1L, 
            "New Root Goal", 
            "Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(10),
            GoalStatusEnum.IN_PROGRESS.getId(),
            null,
            OffsetDateTime.now(),
            null
        );

        when(goalService.createGoal(eq(TEST_USER_ID), any())).thenReturn(responseDto);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalCreateRequestDto request = new GoalCreateRequestDto
        (
            "New Root Goal", 
            "Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(10),
            null
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("New Root Goal"))
                .andExpect(jsonPath("$.parentId").isEmpty());

        verify(goalService).createGoal(eq(TEST_USER_ID), any());
    }

    /**
     * Tests successful creation of a child sub-goal associated with a valid parentId.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should create sub-goal successfully when parentId is valid")
    void createGoalShouldCreateSubGoalSuccessfully() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        GoalResponseDto responseDto = new GoalResponseDto(
            2L, 
            "Child Goal", 
            "Sub description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5),
            GoalStatusEnum.IN_PROGRESS.getId(),
            1L,
            OffsetDateTime.now(),
            null
        );

        when(goalService.createGoal(eq(TEST_USER_ID), any())).thenReturn(responseDto);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalCreateRequestDto request = new GoalCreateRequestDto
        (
            "Child Goal", 
            "Sub description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5),
            1L
        );

        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.parentId").value(1L));

        verify(goalService).createGoal(eq(TEST_USER_ID), any());
    }

    /**
     * Tests POST /goals failure due to type mismatch in JSON payload fields, expecting 400 Bad Request.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 400 Bad Request when payload has type mismatch")
    void createGoalShouldReturn400WhenTypeMismatch() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payload = Map.of(
            "title", "Goal",
            "description", "desc",
            "startDate",List.of(2026.6,6),
            "endDate", "invalid-date",
            "parentId", 1
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /goals failure when mandatory fields are missing, expecting 400 Bad Request (Validation failed).
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 400 Bad Request when required fields are missing")
    void createGoalShouldReturn400WhenValidationFails() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payload = Map.of(
            "description", "Missing required fields"
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /goals failure due to payload having an start date in the past
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 400 Bad Request when payload has start date in the past")
    void createGoalShouldReturn400WhenPayloadHasStartDateInThePast() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();
        LocalDate pastStartIngDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(1);
        Map<String, Object> payload = Map.of(
            "title", "Goal",
            "description", "desc",
            "startDate", pastStartIngDate,
            "endDate", endDate,
            "parentId", 1
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /goals failure due to payload having an startDate null and an endDate not null
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 400 Bad Request when payload has an startDate null and an endDate not null")
    void createGoalShouldReturn400WhenPayloadHasStartDateNullWithEndDateNotNull() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();
        String endDate = LocalDate.now().plusDays(1).toString();
        Map<String, Object> payload = Map.of(
            "title", "Goal",
            "description", "desc",
            "endDate", endDate,
            "parentId", 1
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /goals failure due to payload having an endDate before the startDate
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 400 Bad Request when payload has an endDate before the startDate")
    void createGoalShouldReturn400WhenPayloadHasEndDateBeforeStartDate() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();
        LocalDate  endDate= LocalDate.now().minusDays(1);
        LocalDate  startDate= LocalDate.now().plusDays(1);
        Map<String, Object> payload = Map.of(
            "title", "Goal",
            "description", "desc",
            "startDate", startDate,
            "endDate", endDate,
            "parentId", 1
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /goals failure when trying to add a goal with an invalid start date hierarchy violation.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 400 Bad Request on GoalStartDateViolationException")
    void createGoalShouldReturn400WhenStartDateViolated() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.createGoal(eq(TEST_USER_ID), any()))
                .thenThrow(new GoalStartDateViolationException(LocalDate.now(), LocalDate.now().plusDays(5)));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalCreateRequestDto request = new GoalCreateRequestDto
        (
            "Goal", 
            "Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5),
            1L
        );

        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_GOAL_START_DATE_VIOLATION.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).createGoal(eq(TEST_USER_ID), any());
    }

    /**
     * Tests POST /goals failure when trying to add a goal with an invalid end date hierarchy violation.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 400 Bad Request on GoalEndDateViolationException")
    void createGoalShouldReturn400WhenEndDateViolated() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.createGoal(eq(TEST_USER_ID), any()))
                .thenThrow(new GoalEndDateViolationException(LocalDate.now().plusDays(10), LocalDate.now().plusDays(5)));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalCreateRequestDto request = new GoalCreateRequestDto
        (
            "Goal", 
            "Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5),
            1L
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_GOAL_END_DATE_VIOLATION.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).createGoal(eq(TEST_USER_ID), any());
    }

    /**
     * Tests POST /goals failure when trying to assign a child to a parent goal owned by another user, expecting 403 Forbidden.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 403 Forbidden when parent goal belongs to another user")
    void createGoalShouldReturn403WhenParentNotOwned() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        Long foreignParentId = 99L;

        when(goalService.createGoal(eq(TEST_USER_ID), any()))
                .thenThrow(new GoalOwnershipException(foreignParentId, TEST_USER_ID));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalCreateRequestDto request = new GoalCreateRequestDto
        (
            "Goal", 
            "Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5),
            1L
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_FORBIDDEN.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).createGoal(eq(TEST_USER_ID), any());
    }

    /**
     * Tests POST /goals failure when trying to assign a child to a parent goal that does not exist, expecting 404 Not Found.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /goals - Should return 404 Not Found when parent goal does not exist")
    void createGoalShouldReturn404WhenParentNotFound() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        Long missingParentId = 404L;

        when(goalService.createGoal(eq(TEST_USER_ID), any()))
                .thenThrow(new GoalParentNotFoundException(missingParentId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalCreateRequestDto request = new GoalCreateRequestDto
        (
            "Goal", 
            "Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5),
            missingParentId
        );
        mockMvc.perform(post("/goals")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_NOT_FOUND.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).createGoal(eq(TEST_USER_ID), any());
    }
}