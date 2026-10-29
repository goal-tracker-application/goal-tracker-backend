package com.goal_tracker.goals.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Global and automatic converter to transform the status transition cause enum into its relational integer representation.
 * <p>
 * By being annotated with {@code autoApply = true}, Hibernate will automatically apply this conversion logic
 * to any property of type {@link StatusTransitionCauseEnum} declared in the entity model,
 * eliminating the need for manual annotations on each column.
 * </p>
 * @see StatusTransitionCauseEnum
 */
@Converter(autoApply = true)
public class StatusTransitionCauseConverter implements AttributeConverter<StatusTransitionCauseEnum, Integer> {

    /**
     * Translates the Java Enum value into the corresponding integer to be stored in the database.
     *
     * @param attribute The business enum to evaluate.
     * @return The integer identifier, or {@code null} if the attribute is null.
     */
    @Override
    public Integer convertToDatabaseColumn(StatusTransitionCauseEnum attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getId();
    }

    /**
     * Translates the integer coming from the database back into the strongly typed Java constant.
     *
     * @param dbData The integer stored in the 'cause_id' column.
     * @return The mapped {@link StatusTransitionCauseEnum}, or {@code null} if the database value is null.
     */
    @Override
    public StatusTransitionCauseEnum convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return StatusTransitionCauseEnum.fromId(dbData);
    }
}