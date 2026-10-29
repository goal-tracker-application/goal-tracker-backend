package com.goal_tracker.goals.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Global and automatic converter to transform the enum into its relational integer representation.
 * <p>
 * By being annotated with {@code autoApply = true}, Hibernate will automatically apply this conversion logic
 * to any property of type {@link GoalStatusEnum} declared in the entity model,
 * eliminating the need for manual annotations on each column.
 * </p>
 * @see GoalStatusEnum
 */
@Converter(autoApply = true)
public class GoalStatusConverter implements AttributeConverter<GoalStatusEnum, Integer> {

    /**
     * Translates the Java Enum value into the corresponding integer to be stored in the database.
     *
     * @param attribute The business enum to evaluate.
     * @return The integer identifier, or {@code null} if the attribute is null.
     */
    @Override
    public Integer convertToDatabaseColumn(GoalStatusEnum attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getId();
    }

    /**
     * Translates the integer coming from the database back into the strongly typed Java constant.
     *
     * @param dbData The integer stored in the 'status_id' column.
     * @return The mapped {@link GoalStatusEnum}, or {@code null} if the database value is null.
     */
    @Override
    public GoalStatusEnum convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return GoalStatusEnum.fromId(dbData);
    }
}