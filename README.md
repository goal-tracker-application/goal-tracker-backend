# goal-tracker-backend

[Read this README in Spanish (Español)](README.es.md)

RESTful API backend service for Goal Tracker built with Spring Boot, PostgreSQL, and Flyway. Handles user authentication (JWT), secure account management, version-controlled database migrations, cascading recursive goal trees, and automated maintenance tasks.

## 1. Error Handling

The API implements a global exception interceptor `GlobalExceptionHandler` that unifies all failed responses under a strict semantic contract based on a domain enum `ErrorCode`, preventing the leakage of internal server stack traces.

### General Error Structure (ErrorResponse)

All API error responses are standardized under the `ErrorResponse` Data Transfer Object. Upon any failure, the system automatically generates a unique tracking identifier `trackingId` based on a UUID v4 prefixed with ERR-. This identifier is replicated across audit logs and server logs to allow rapid traceability and operational support.

#### JSON Example

``` json
{
  "code": "ERR_VALIDATION_FAILED",
  "message": "The submitted data does not meet the required validations.",
  "details": [
    "The field 'title' cannot be blank.",
    "The target value must be greater than 0."
  ],
  "trackingId": "ERR-7c9b1a2d-4e8f-4b3c-9a1d-5e6f7a8b9c0d"
}
```

#### TypeScript Definition

For frontend integration (e.g., in TypeScript/React or Angular clients), the error response contract is modeled as follows:

``` typescript
export interface ErrorResponse {
  code: string;
  message: string;
  details?: string[];
  trackingId: string;
}
```

> **Serialization Behavior:**  
> The `details` property is included as an array of explanatory strings or field validation specifications. If there is no supplementary information regarding the failure, the `details` key is automatically omitted from the JSON body sent to the client thanks to the serialization rule `@JsonInclude(JsonInclude.Include.NON_EMPTY)`.

### Available Error Codes

| Static Code | Description / Root Cause | HTTP Status |
| :--- | :--- | :--- |
| `ERR_VALIDATION_FAILED` | Input validation failures (`@Valid` on DTOs or constraints violations). Includes detailed explanatory messages per field. | 400 Bad Request |
| `ERR_TYPE_MISMATCH` | Malformed URL/Query parameters or strict type coercion failures in parameters (e.g., sending text where a numeric ID is expected). | 400 Bad Request |
| `ERR_UNAUTHORIZED` | The request lacks valid authentication credentials, the token has expired, or the provided access data is incorrect. | 401 Unauthorized |
| `ERR_FORBIDDEN` | The authenticated user attempts to access a resource or perform an action for which they lack sufficient permissions. | 403 Forbidden |
| `ERR_NOT_FOUND` | The requested resource does not exist in the database or is not active in the system. | 404 Not Found |
| `ERR_EMAIL_ALREADY_EXISTS` | Occurs when attempting to register a user with an email address that is already occupied by another account. | 409 Conflict |
| `ERR_DUPLICATE_GOAL_LOG` | Occurs when attempting to create a progress entry (check-in) for a goal on a date that already has an existing entry. | 409 Conflict |
| `ERR_INVALID_GOAL_HIERARCHY` | Occurs when the goal hierarchy is invalid (e.g., a goal cannot be its own parent or generate circular dependencies). | 409 Conflict |
| `ERR_GOAL_DATE_RANGE_INVALID` | Occurs when the start date is later than the end date for a goal. | 422 Unprocessable Entity |
| `ERR_INTERNAL_SERVER_ERROR` | Final safety net to capture unexpected system exceptions (connectivity drops, null pointers, or unforeseen infrastructure failures). Full traceability remains hidden behind the `trackingId`. | 500 Internal Server Error |