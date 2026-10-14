package com.goal_tracker.errors;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class ErrorResponseDeserializer extends ValueDeserializer<ErrorResponse> {

    /**
     * Deserializes an ErrorResponse object from JSON.
     *
     * @param p    the JsonParser to read from
     * @param ctxt the DeserializationContext
     * @return the deserialized ErrorResponse object
     * @throws JacksonException if there is a problem during deserialization
     */
    @Override
    public ErrorResponse deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        String code = null;
        String message = null;
        List<String> details = new ArrayList<>();
        String trackingId = null;

        if (p.currentToken() != JsonToken.START_OBJECT) {
            JsonToken t = p.nextToken();
            if (t != JsonToken.START_OBJECT) {
                throw ctxt.wrongTokenException(p,t.getClass(), JsonToken.START_OBJECT, "Expected START_OBJECT for ErrorResponse deserialization");
            }
        }

        while (p.nextToken() != JsonToken.END_OBJECT) {
            String propName = p.currentName();
            p.nextToken(); // Mover al valor

            switch (propName) {
                case "code":
                    code = p.getValueAsString();
                    break;
                case "message":
                    message = p.getValueAsString();
                    break;
                case "details":
                    if (p.currentToken() == JsonToken.START_ARRAY) {
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            String detail = p.getValueAsString();
                            if (detail != null) {
                                details.add(detail);
                            }
                        }
                    }
                    break;
                case "trackingId":
                    trackingId = p.getValueAsString();
                    break;
                default:
                    p.skipChildren();
                    break;
            }
        }

        if (code == null) {
            throw new IllegalArgumentException("Deserialization error: 'code' cannot be null");
        }
        if (trackingId == null) {
            throw new IllegalArgumentException("Deserialization error: 'trackingId' cannot be null");
        }

        return new ErrorResponse(code, message, details, trackingId);
    }
}