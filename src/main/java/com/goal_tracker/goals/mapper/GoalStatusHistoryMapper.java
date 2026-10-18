package com.goal_tracker.goals.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import com.goal_tracker.goals.dto.GoalStatusHistoryResponseDto;
import com.goal_tracker.goals.entity.GoalStatusHistory;

/**
 * Declarative mapping contract to transform the object graph of goal status audit histories.
 * <p>
 * The policy {@code unmappedTargetPolicy = ReportingPolicy.ERROR} forces the compiler to fail
 * immediately if any property in the target DTO is not explicitly mapped or matched by name
 * from the source entity.
 * </p>
 *
 * @see GoalStatusHistory
 * @see GoalStatusHistoryResponseDto
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface GoalStatusHistoryMapper {

    /**
     * Transforms a persistence entity of type {@link GoalStatusHistory} into its corresponding DTO {@link GoalStatusHistoryResponseDto}.
     * <p>
     * Maps the business status enum to its numerical identifier (`statusId`) via expression mapping.
     * </p>
     *
     * @param history Source audit entity retrieved from the database.
     * @return Immutable {@link GoalStatusHistoryResponseDto} to be exposed in the API.
     */
    @Mapping(target = "statusId", expression = "java(history.getStatus() != null ? history.getStatus().getId() : null)")
    @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
    GoalStatusHistoryResponseDto toResponse(GoalStatusHistory history);
}