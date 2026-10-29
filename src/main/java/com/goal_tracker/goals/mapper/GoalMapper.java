package com.goal_tracker.goals.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.dto.GoalTreeResponseDto;
import com.goal_tracker.goals.dto.GoalUpdateRequestDto;
import com.goal_tracker.goals.entity.Goal;

/**
 * Declarative mapping contract to transform the object graph of the Goal domain.
 * <p>
 * The policy {@code unmappedTargetPolicy = ReportingPolicy.ERROR} forces the compiler to fail
 * immediately if any property in the target entity or DTO is not explicitly mapped or matched by name
 * from the source object.
 * </p>
 *
 * @see Goal
 * @see GoalResponseDto
 * @see GoalTreeResponseDto
 * @see GoalCreateRequestDto
 * @see GoalUpdateRequestDto
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface GoalMapper {

    /**
     * Transforms a persistence entity of type {@link Goal} into its corresponding flat DTO {@link GoalResponseDto}.
     *
     * @param goal Source entity retrieved from the database.
     * @return Immutable {@link GoalResponseDto} to be exposed in the API.
     */
    @Mapping(target = "statusId", expression = "java(goal.getStatus() != null ? goal.getStatus().getId() : null)")
    @Mapping(target = "parentId", expression = "java(goal.getParent() != null ? goal.getParent().getId() : null)")
    @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
    GoalResponseDto toResponse(Goal goal);

    /**
     * Transforms a persistence entity of type {@link Goal} and its hierarchy into the tree DTO {@link GoalTreeResponseDto}.
     *
     * @param goal Source entity along with its sub-goals object graph.
     * @return Recursively structured {@link GoalTreeResponseDto}.
     */
    @Mapping(target = "statusId", expression = "java(goal.getStatus() != null ? goal.getStatus().getId() : null)")
    @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
    GoalTreeResponseDto toTreeResponse(Goal goal);

    /**
     * Maps the creation DTO {@link GoalCreateRequestDto} to the domain entity {@link Goal}.
     * <p>
     * Control fields, identifiers, business-managed status relationships, sub-goal hierarchies,
     * and audit timestamps are explicitly ignored.
     * </p>
     *
     * @param request Input DTO containing new goal details.
     * @return Instance of {@link Goal} ready to undergo service layer logic.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusRegister", ignore= true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subGoals", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Goal toEntity(GoalCreateRequestDto request);

    /**
     * Updates an existing {@link Goal} entity using values from {@link GoalUpdateRequestDto}.
     * <p>
     * Structural and audit attributes that must not be modified through a basic update are ignored.
     * </p>
     *
     * @param request Update DTO containing basic new values.
     * @param goal    Target existing entity to be mutated with updated attributes.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusRegister", ignore= true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subGoals", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(GoalUpdateRequestDto request, @MappingTarget Goal goal);
}