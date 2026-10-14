package com.goal_tracker.errors;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Custom serializer for ErrorResponse objects.
 */
public class ErrorResponseSerializer extends ValueSerializer<ErrorResponse> {

    /**
     * Checks if the ErrorResponse object is empty (null).
     *
     * @param ctxt the SerializationContext providing context for serialization
     * @param value the ErrorResponse object to check
     * @return true if the ErrorResponse object is null, false otherwise
     */
    @Override
    public boolean isEmpty(SerializationContext ctxt, ErrorResponse value) {
        return value == null;
    }

    /**
     * Serializes an ErrorResponse object into JSON format.
     *
     * @param value the ErrorResponse object to serialize
     * @param gen the JsonGenerator used to write JSON content
     * @param ctxt the SerializationContext providing context for serialization
     * @throws JacksonException if any required field is null or if serialization fails
     */
    @Override
    public void serialize(ErrorResponse value, JsonGenerator gen, SerializationContext ctxt) 
            throws JacksonException {
        if (value.getCode() == null) {
            throw new JacksonException("ErrorResponse 'code' cannot be null") {};
        }
        if (value.getMessage() == null) {
            throw new JacksonException("ErrorResponse 'message' cannot be null") {} ;
        }
        if (value.getTrackingId() == null) {
            throw new JacksonException("ErrorResponse 'trackingId' cannot be null") {};
        }
        gen.writeStartObject();
        
        gen.writeStringProperty("code", value.getCode());
        gen.writeStringProperty("message", value.getMessage());
        
        if (value.getDetails() != null && !value.getDetails().isEmpty()) {
            gen.writeArrayPropertyStart("details");
            for (String detail : value.getDetails()) {
                gen.writeString(detail);
            }
            gen.writeEndArray();
        }
        gen.writeStringProperty("trackingId", value.getTrackingId());
        
        gen.writeEndObject();
    }
}