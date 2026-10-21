package com.goal_tracker.goals.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.goal_tracker.config.security.details.UserPrincipal;
import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.GlobalExceptionHandler;
import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.dto.GoalStatusChangeRequestDto;
import com.goal_tracker.goals.dto.GoalTreeResponseDto;
import com.goal_tracker.goals.dto.GoalUpdateRequestDto;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.exceptions.GoalEndDateViolationException;
import com.goal_tracker.goals.exceptions.GoalNotFoundException;
import com.goal_tracker.goals.exceptions.GoalOwnershipException;
import com.goal_tracker.goals.exceptions.GoalParentNotFoundException;
import com.goal_tracker.goals.exceptions.GoalStartDateViolationException;
import com.goal_tracker.goals.exceptions.GoalStatusNotFoundException;
import com.goal_tracker.goals.exceptions.IllegalGoalTransitionException;
import com.goal_tracker.goals.service.GoalService;

import jakarta.validation.Validator;
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

public class GoalControllerUnitTest {

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


        LocalValidatorFactoryBean localValidatorFactory = new LocalValidatorFactoryBean();
        localValidatorFactory.afterPropertiesSet();

        Validator jakartaValidator = localValidatorFactory.getValidator();
        MethodValidationInterceptor interceptor = new MethodValidationInterceptor(jakartaValidator);

        ProxyFactory proxyFactory = new ProxyFactory(goalController);
        proxyFactory.addAdvice(interceptor);
        GoalController goalControllerProxy = (GoalController) proxyFactory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(goalControllerProxy)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(localValidatorFactory) 
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
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        
        when(goalService.getRootGoals(eq(TEST_USER_ID), isNull())).thenReturn(Collections.emptyList());
        
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
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
    @DisplayName("GET /goals/roots - Should filter root goals by status identifier successfully")
    void getRootGoalsShouldFilterByStatusSuccessfully() throws Exception {
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
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        
        Integer invalidStatusId = 999;

        when(goalService.getRootGoals(eq(TEST_USER_ID), eq(invalidStatusId)))
                .thenThrow(new GoalStatusNotFoundException(invalidStatusId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
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
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context =SecurityContextHolder.getContext();
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
     * Tests response handling when requesting a goal tree for an goalId id with invalid type
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("GET /goals/{id}/tree - Should return 400 when goalId is of an invalid type")
    void getGoalTreeShouldReturn400WhenGoalIdHasInvalidType() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(get("/goals/{id}/tree", "invalidType")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
        verify(goalService, never()).getGoalTree(eq(TEST_USER_ID), anyLong());
    }

    /**
     * Tests response handling when requesting a goal tree for an invalid goalId
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("GET /goals/{id}/tree - Should return 400 for an invalid goalId")
    void getGoalTreeShouldReturn400WhenGoalIdIsInvalid() throws Exception {
        Long invalidId = -1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(get("/goals/{id}/tree", invalidId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
        verify(goalService, never()).getGoalTree(eq(TEST_USER_ID), eq(invalidId));
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

    /**
     * Tests successful update of a root goal.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should update root goal successfully when payload is valid")
    void updateGoalShouldUpdateRootGoalSuccessfully() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        GoalResponseDto responseDto = new GoalResponseDto(
            goalId, 
            "Updated Root Goal", 
            "Updated Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(10),
            GoalStatusEnum.IN_PROGRESS.getId(),
            null,
            OffsetDateTime.now(),
            null
        );

        when(goalService.updateGoal(eq(TEST_USER_ID), eq(goalId), any())).thenReturn(responseDto);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Updated Root Goal", 
            "Updated Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(10)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(goalId))
                .andExpect(jsonPath("$.title").value("Updated Root Goal"));

        verify(goalService).updateGoal(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests successful update of a child goal.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should update child goal successfully when payload is valid")
    void updateGoalShouldUpdateChildGoalSuccessfully() throws Exception {
        Long goalId = 2L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        GoalResponseDto responseDto = new GoalResponseDto(
            goalId, 
            "Updated Child Goal", 
            "Updated Sub Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5),
            GoalStatusEnum.IN_PROGRESS.getId(),
            1L,
            OffsetDateTime.now(),
            null
        );

        when(goalService.updateGoal(eq(TEST_USER_ID), eq(goalId), any())).thenReturn(responseDto);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Updated Child Goal", 
            "Updated Sub Description", 
            LocalDate.now().plusDays(1), 
            LocalDate.now().plusDays(6)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(goalId))
                .andExpect(jsonPath("$.title").value("Updated Child Goal"))
                .andExpect(jsonPath("$.parentId").value(1L));

        verify(goalService).updateGoal(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id} failure due to type mismatch in the path variable id.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request when goal id has type mismatch")
    void updateGoalShouldReturn400WhenIdTypeMismatch() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Goal", "Desc", LocalDate.now(), LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", "invalidType")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).updateGoal(eq(TEST_USER_ID), any(), eq(request));
    }

    /**
     * Tests PATCH /goals/{id} failure due to validation error when goal id is zero or negative.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request when goal id fails validation (zero or negative)")
    void updateGoalShouldReturn400WhenIdValidationFails() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Goal", "Desc", LocalDate.now(), LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", 0L)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).updateGoal(eq(TEST_USER_ID), eq(0L), eq(request));
    }

    /**
     * Tests PATCH /goals/{id} failure due to type mismatch in the payload fields.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request when payload has type mismatch")
    void updateGoalShouldReturn400WhenPayloadTypeMismatch() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payload = Map.of(
            "title", "Goal",
            "description", "desc",
            "startDate", "invalid-date",
            "endDate", LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
        
        verify(goalService, never()).updateGoal(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id} failure when required payload fields are missing.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request when required payload fields are missing")
    void updateGoalShouldReturn400WhenPayloadValidationFails() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payload = Map.of(
            "description", "Missing title"
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
        
        verify(goalService, never()).updateGoal(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id} failure for a payload with startDate in the past
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request for a payload with start date in the past")
    void updateGoalShouldReturn400WhenStartDateIsInThePast() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payloadPastStart = Map.of(
            "title", "Goal",
            "description", "desc",
            "startDate", LocalDate.now().minusDays(1),
            "endDate", LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadPastStart)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()));
    }


    /**
     * Tests PATCH /goals/{id} failure for a payload with startDate null and end date not null
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request for a payload with startDate null and end date not null")
    void updateGoalShouldReturn400WhenStartDateIsNullWhenEndDateNotNUll() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> invalidPayload = Map.of(
            "title", "Goal",
            "description", "desc",
            "endDate", LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()));
    }


    /**
     * Tests PATCH /goals/{id} failure for a payload with startDate after endDate
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request for a payload with start date after end date")
    void updateGoalShouldReturn400WhenStartDateIsAfterEndDatet() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payloadPastStart = Map.of(
            "title", "Goal",
            "description", "desc",
            "startDate", LocalDate.now().plusDays(4),
            "endDate", LocalDate.now()
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadPastStart)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()));

    }

    /**
     * Tests PATCH /goals/{id} failure when start date violates hierarchical constraints with ancestors or descendants.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request on GoalStartDateViolationException")
    void updateGoalShouldReturn400WhenStartDateHierarchyViolated() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.updateGoal(eq(TEST_USER_ID), eq(goalId), any()))
                .thenThrow(new GoalStartDateViolationException(LocalDate.now(), LocalDate.now().plusDays(5)));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Goal", "Desc", LocalDate.now(), LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_GOAL_START_DATE_VIOLATION.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).updateGoal(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id} failure when end date violates hierarchical constraints.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 400 Bad Request on GoalEndDateViolationException")
    void updateGoalShouldReturn400WhenEndDateHierarchyViolated() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.updateGoal(eq(TEST_USER_ID), eq(goalId), any()))
                .thenThrow(new GoalEndDateViolationException(LocalDate.now().plusDays(10), LocalDate.now().plusDays(5)));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Goal", "Desc", LocalDate.now(), LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_GOAL_END_DATE_VIOLATION.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).updateGoal(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id} failure when trying to update a goal belonging to another user, expecting 403 Forbidden.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 403 Forbidden when goal belongs to another user")
    void updateGoalShouldReturn403WhenGoalNotOwned() throws Exception {
        Long goalId = 50L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.updateGoal(eq(TEST_USER_ID), eq(goalId), any()))
                .thenThrow(new GoalOwnershipException(goalId, TEST_USER_ID));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Goal", "Desc", LocalDate.now(), LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_FORBIDDEN.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).updateGoal(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id} failure when the target goal does not exist, expecting 404 Not Found.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id} - Should return 404 Not Found when goal does not exist")
    void updateGoalShouldReturn404WhenGoalNotFound() throws Exception {
        Long nonexistentId = 99L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.updateGoal(eq(TEST_USER_ID), eq(nonexistentId), any()))
                .thenThrow(new GoalNotFoundException(nonexistentId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalUpdateRequestDto request = new GoalUpdateRequestDto(
            "Goal", "Desc", LocalDate.now(), LocalDate.now().plusDays(5)
        );

        mockMvc.perform(patch("/goals/{id}", nonexistentId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_NOT_FOUND.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).updateGoal(eq(TEST_USER_ID), eq(nonexistentId), any());
    }

    /**
     * Tests successful status update of a goal via PATCH /goals/{id}/status.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should update goal status successfully when payload is valid")
    void changeGoalStatusShouldUpdateSuccessfully() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        GoalResponseDto responseDto = new GoalResponseDto(
            goalId, 
            "Goal", 
            "Description", 
            LocalDate.now(), 
            LocalDate.now().plusDays(10),
            GoalStatusEnum.IN_PROGRESS.getId(),
            null,
            OffsetDateTime.now(),
            null
        );

        when(goalService.changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any())).thenReturn(responseDto);

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.IN_PROGRESS.getId(),
            "Starting goal"
        );

        mockMvc.perform(patch("/goals/{id}/status", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(goalId))
                .andExpect(jsonPath("$.statusId").value(GoalStatusEnum.IN_PROGRESS.getId()));

        verify(goalService).changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure due to type mismatch in the path variable id.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request when goal id has type mismatch")
    void changeGoalStatusShouldReturn400WhenIdTypeMismatch() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.IN_PROGRESS.getId(), "Notes"
        );

        mockMvc.perform(patch("/goals/invalid-id/status")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).changeGoalStatus(eq(TEST_USER_ID), any(), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when goal id is zero or negative.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request when goal id fails validation (zero or negative)")
    void changeGoalStatusShouldReturn400WhenIdValidationFails() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.IN_PROGRESS.getId(), "Notes"
        );

        mockMvc.perform(patch("/goals/{id}/status", 0L)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).changeGoalStatus(eq(TEST_USER_ID), eq(0L), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure due to type mismatch in the payload fields.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request when payload has type mismatch")
    void changeGoalStatusShouldReturn400WhenPayloadTypeMismatch() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payload = Map.of(
            "statusId", "invalid-status-type",
            "notes", "Notes"
        );

        mockMvc.perform(patch("/goals/{id}/status", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when payload validation fails (e.g., missing statusId).
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request when payload validation fails")
    void changeGoalStatusShouldReturn400WhenPayloadValidationFails() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payload = Map.of(
            "notes", "Missing statusId"
        );

        mockMvc.perform(patch("/goals/{id}/status", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when payload validation fails invalid status_id
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request when payload validation fails for an invalid status id")
    void changeGoalStatusShouldReturn400WhenPayloadValidationFailsForAnInvalidStatusId() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        Map<String, Object> payload = Map.of(
            "statusId", 8,
            "notes", "Invalid statusId"
        );

        mockMvc.perform(patch("/goals/{id}/status", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when the goal is not found, expecting 404 Not Found.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 404 Not Found when goal does not exist")
    void changeGoalStatusShouldReturn404WhenGoalNotFound() throws Exception {
        Long nonexistentId = 99L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.changeGoalStatus(eq(TEST_USER_ID), eq(nonexistentId), any()))
                .thenThrow(new GoalNotFoundException(nonexistentId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.IN_PROGRESS.getId(), "Notes"
        );

        mockMvc.perform(patch("/goals/{id}/status", nonexistentId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_NOT_FOUND.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).changeGoalStatus(eq(TEST_USER_ID), eq(nonexistentId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when goal belongs to another user, expecting 403 Forbidden.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 403 Forbidden when goal belongs to another user")
    void changeGoalStatusShouldReturn403WhenGoalNotOwned() throws Exception {
        Long foreignGoalId = 50L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.changeGoalStatus(eq(TEST_USER_ID), eq(foreignGoalId), any()))
                .thenThrow(new GoalOwnershipException(foreignGoalId, TEST_USER_ID));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.IN_PROGRESS.getId(), "Notes"
        );

        mockMvc.perform(patch("/goals/{id}/status", foreignGoalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_FORBIDDEN.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).changeGoalStatus(eq(TEST_USER_ID), eq(foreignGoalId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when status transition is unchanged (ERR_GOAL_STATUS_UNCHANGED).
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request on ERR_GOAL_STATUS_UNCHANGED")
    void changeGoalStatusShouldReturn400WhenStatusUnchanged() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any()))
                .thenThrow(new IllegalGoalTransitionException(goalId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.IN_PROGRESS.getId(), "Same status"
        );

        mockMvc.perform(patch("/goals/{id}/status", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_GOAL_STATUS_UNCHANGED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when goal is in terminal state (ERR_GOAL_TERMINAL_STATE).
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request on ERR_GOAL_TERMINAL_STATE")
    void changeGoalStatusShouldReturn400WhenTerminalState() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any()))
                .thenThrow(new IllegalGoalTransitionException(goalId, GoalStatusEnum.COMPLETED));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.IN_PROGRESS.getId(), "Modify completed"
        );

        mockMvc.perform(patch("/goals/{id}/status", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_GOAL_TERMINAL_STATE.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests PATCH /goals/{id}/status failure when transition is invalid (ERR_INVALID_GOAL_TRANSITION).
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("PATCH /goals/{id}/status - Should return 400 Bad Request on ERR_INVALID_GOAL_TRANSITION")
    void changeGoalStatusShouldReturn400WhenInvalidTransition() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        when(goalService.changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any()))
                .thenThrow(new IllegalGoalTransitionException(goalId, GoalStatusEnum.WAITING, GoalStatusEnum.COMPLETED));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        GoalStatusChangeRequestDto request = new GoalStatusChangeRequestDto(
            GoalStatusEnum.WAITING.getId(), "Invalid transition"
        );

        mockMvc.perform(patch("/goals/{id}/status", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_GOAL_TRANSITION.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).changeGoalStatus(eq(TEST_USER_ID), eq(goalId), any());
    }

    /**
     * Tests successful deletion of a goal via DELETE /goals/{id}.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("DELETE /goals/{id} - Should delete goal successfully when id is valid and owned")
    void deleteGoalShouldDeleteSuccessfully() throws Exception {
        Long goalId = 1L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        Mockito.doNothing().when(goalService).deleteGoal(eq(TEST_USER_ID), eq(goalId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(delete("/goals/{id}", goalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(goalService).deleteGoal(eq(TEST_USER_ID), eq(goalId));
    }

    /**
     * Tests DELETE /goals/{id} failure due to type mismatch in the path variable id.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("DELETE /goals/{id} - Should return 400 Bad Request when goal id has type mismatch")
    void deleteGoalShouldReturn400WhenIdTypeMismatch() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(delete("/goals/{id}", "invalidType")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).deleteGoal(any(), any());
    }

    /**
     * Tests DELETE /goals/{id} failure when goal id is zero or negative.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("DELETE /goals/{id} - Should return 400 Bad Request when goal id fails validation (zero or negative)")
    void deleteGoalShouldReturn400WhenIdValidationFails() throws Exception {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(delete("/goals/{id}", 0L)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService, never()).deleteGoal(any(), eq(0L));
    }

    /**
     * Tests DELETE /goals/{id} failure when goal belongs to another user, expecting 403 Forbidden.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("DELETE /goals/{id} - Should return 403 Forbidden when goal belongs to another user")
    void deleteGoalShouldReturn403WhenGoalNotOwned() throws Exception {
        Long foreignGoalId = 50L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        Mockito.doThrow(new GoalOwnershipException(foreignGoalId, TEST_USER_ID))
                .when(goalService).deleteGoal(eq(TEST_USER_ID), eq(foreignGoalId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(delete("/goals/{id}", foreignGoalId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_FORBIDDEN.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).deleteGoal(eq(TEST_USER_ID), eq(foreignGoalId));
    }

    /**
     * Tests DELETE /goals/{id} failure when the goal is not found, expecting 404 Not Found.
     * 
     * @throws Exception if the MockMvc request execution fails
     */
    @Test
    @DisplayName("DELETE /goals/{id} - Should return 404 Not Found when goal does not exist")
    void deleteGoalShouldReturn404WhenGoalNotFound() throws Exception {
        Long nonexistentId = 99L;
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "owner@goaltracker.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        Mockito.doThrow(new GoalNotFoundException(nonexistentId))
                .when(goalService).deleteGoal(eq(TEST_USER_ID), eq(nonexistentId));

        SecurityContextHolder.getContext().setAuthentication(auth);
        SecurityContext context = SecurityContextHolder.getContext();

        mockMvc.perform(delete("/goals/{id}", nonexistentId)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_NOT_FOUND.name()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));

        verify(goalService).deleteGoal(eq(TEST_USER_ID), eq(nonexistentId));
    }
}