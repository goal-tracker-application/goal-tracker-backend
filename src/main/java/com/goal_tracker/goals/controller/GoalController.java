package com.goal_tracker.goals.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.goal_tracker.config.security.details.UserPrincipal;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.dto.GoalTreeResponseDto;
import com.goal_tracker.goals.service.GoalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for managing goal operations, providing endpoints for
 * creation, updates, status transitions, tree retrievals, and deletions.
 * <p>
 * Exposes secure endpoints bound to authenticated user sessions via JWT principals.
 * </p>
 *
 * @see GoalService
 */
@RestController
@RequestMapping(value = "/goals", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Goals", description = "Endpoints for managing user goals, hierarchical structures, and life-cycle operations")
public class GoalController {

    private final GoalService goalService;

    /**
     * Constructs a new {@code GoalController} with the required goal service dependency.
     *
     * @param goalService the service handling goal business logic operations
     */
    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * Retrieves root-level goals for the authenticated user, optionally filtered by a status identifier.
     *
     * @param statusId    optional numeric identifier to filter root goals by status
     * @param principal   the currently authenticated user principal extracted from security context
     * @return a {@link ResponseEntity} containing the list of root-level goal response DTOs
     */
    @GetMapping("/roots")
    @Operation(
        summary = "Get root goals",
        description = "Retrieves all top-level root goals belonging to the authenticated user, with an optional filter by status ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the list of root goals (can be empty)."),
        @ApiResponse(responseCode = "400", description = "Invalid status identifier provided."),
        @ApiResponse(responseCode = "401", description = "Unauthorized access or missing valid JWT token.")
    })
    public ResponseEntity<List<GoalResponseDto>> getRootGoals(
            @Parameter(description = "Optional status ID to filter root goals")
            @RequestParam(name = "status_id", required = false) Integer statusId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<GoalResponseDto> rootGoals = goalService.getRootGoals(principal.id(), statusId);
        return ResponseEntity.ok(rootGoals);
    }

    /**
     * Retrieves the complete hierarchical tree of a goal for the authenticated user.
     *
     * @param goalId      the unique identifier of the target root or parent goal
     * @param principal   the currently authenticated user principal extracted from security context
     * @return a {@link ResponseEntity} containing the hierarchical {@link GoalTreeResponseDto} structure
     */
    @GetMapping("/{id}/tree")
    @Operation(
        summary = "Get goal tree structure",
        description = "Retrieves the complete hierarchical tree of a goal along with its sub-goals for the authenticated user."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the goal tree structure."),
        @ApiResponse(responseCode = "401", description = "Unauthorized access or missing valid JWT token."),
        @ApiResponse(responseCode = "403", description = "Access denied due to goal ownership mismatch."),
        @ApiResponse(responseCode = "404", description = "Goal not found.")
    })
    public ResponseEntity<GoalTreeResponseDto> getGoalTree(
            @Parameter(description = "Numeric identifier of the target goal")
            @PathVariable("id") Long goalId,
            @AuthenticationPrincipal UserPrincipal principal) {
        GoalTreeResponseDto goalTree = goalService.getGoalTree(principal.id(), goalId);
        return ResponseEntity.ok(goalTree);
    }

}