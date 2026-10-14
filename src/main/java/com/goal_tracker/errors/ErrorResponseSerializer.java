package com.goal_tracker.errors;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class ErrorResponseSerializer extends ValueSerializer<ErrorResponse> {

    @Override
    public boolean isEmpty(SerializationContext ctxt, ErrorResponse value) {
        return value == null;
    }
    
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