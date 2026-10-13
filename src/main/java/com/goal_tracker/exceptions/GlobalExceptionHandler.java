package com.goal_tracker.exceptions;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.errors.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

/**
 * Global exception interceptor for the API.
 * <p>
 * Centralizes failure handling in the controller layer (Web/HTTP), formatting
 * error responses under a unified and semantic contract using {@link ErrorResponse} 
 * based on {@link ErrorCode}.
 * Prevents unintentional exposure of internal server stack traces or database structures.
 * </p>
 * 
 * @see ErrorCode
 * @see ErrorResponse
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Intercepts validation failures produced when processing request bodies (DTOs).
     * Triggered automatically when a parameter annotated with {@code @Valid} or {@code @Validated}
     * fails JSR-303 constraint checks.
     *
     * @param ex Captured exception with binding errors.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorId = generateTrackingId();
        
        log.warn("[{}] Validation error detected in DTO fields (JSR-303):", errorId);
        
        List<String> fieldDetails = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    log.warn("[{}]  -> Field '{}': {} (Rejected value: [{}])", 
                        errorId, error.getField(), error.getDefaultMessage(), error.getRejectedValue());
                    
                    return String.format("The field '%s' %s.", error.getField(), error.getDefaultMessage());
                })
                .toList();

        ErrorResponse errorResponse = new ErrorResponse(
            ErrorCode.ERR_VALIDATION_FAILED.name(),
            ErrorCode.ERR_VALIDATION_FAILED.getDefaultMessage(),
            fieldDetails,
            errorId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Intercepts type mismatches or incompatibilities detected in URL input parameters.
     * Example: Passing an alphanumeric string in a {@code @PathVariable} or {@code @RequestParam} 
     * that expects a numeric type.
     *
     * @param ex Exception caused by parameter type mismatch in the request.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String errorId = generateTrackingId();
        String field = ex.getName();
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        
        String detailMessage = String.format("The parameter '%s' must be of type %s.", field, requiredType);
        
        log.warn("[{}] Type mismatch in URL parameter '{}'. Required type: '{}', received value: '[{}]'. Detail: '{}'", 
            errorId, field, requiredType, ex.getValue(), detailMessage);

        ErrorResponse errorResponse = new ErrorResponse(
            ErrorCode.ERR_TYPE_MISMATCH.name(),
            ErrorCode.ERR_TYPE_MISMATCH.getDefaultMessage(),
            detailMessage,
            errorId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles invalid credential exceptions generated during authentication.
     * <p>
     * Captures instances of {@link BadCredentialsException} thrown by Spring Security
     * when a user submits wrong credentials and returns a structured response
     * with an HTTP status 401 (Unauthorized).
     * </p>
     *
     * @param ex The captured {@link BadCredentialsException}.
     * @param request The current {@link HttpServletRequest}.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 401.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        String errorId = generateTrackingId();

        log.info("[{}] Invalid credentials. Code: '{}', Detail: '{}'", 
            errorId, ErrorCode.ERR_UNAUTHORIZED.name(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            ErrorCode.ERR_UNAUTHORIZED.name(),
            ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage(),
            List.of(ex.getMessage()),
            errorId
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Centrally intercepts and processes any exception inheriting from {@link ForbiddenException}.
     *
     * @param ex Exception thrown when access is denied due to insufficient permissions.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 403.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenExceptions(ForbiddenException ex) {
        String errorId = generateTrackingId();
        
        log.warn("[{}] Access denied (Forbidden). Error Code: '{}', Detail: '{}'", 
            errorId, ex.getErrorCode().name(), ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode().name(),
            ex.getErrorCode().getDefaultMessage(),
            ex.getMessage(),
            errorId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Centrally intercepts any exception inheriting from {@link NotFoundException}.
     *
     * @param ex Exception thrown when an explicitly requested resource is missing.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 404.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundExceptions(NotFoundException ex) {
        String errorId = generateTrackingId();
        
        log.info("[{}] Requested resource not found. Code: '{}', Detail: '{}'", 
            errorId, ex.getErrorCode().name(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode().name(),
            ex.getErrorCode().getDefaultMessage(),
            ex.getMessage(),
            errorId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Centrally intercepts and processes any exception inheriting from {@link ConflictException}.
     *
     * @param ex Exception thrown upon a state or resource conflict.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 409.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictExceptions(ConflictException ex) {
        String errorId = generateTrackingId();
        
        log.warn("[{}] Conflict detected. Error Code: '{}', Detail: '{}'", 
            errorId, ex.getErrorCode().name(), ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode().name(),
            ex.getErrorCode().getDefaultMessage(),
            ex.getMessage(),
            errorId
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Centrally intercepts and processes any exception inheriting from {@link BusinessException}.
     *
     * @param ex Exception thrown upon violating a domain business rule.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 422.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessExceptions(BusinessException ex) {
        String errorId = generateTrackingId();
        
        log.warn("[{}] Business rule violation captured. Domain Code: '{}', Detail: '{}'", 
            errorId, ex.getErrorCode().name(), ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode().name(),
            ex.getErrorCode().getDefaultMessage(),
            ex.getMessage(),
            errorId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(errorResponse);
    }

    /**
     * Final safety net to contain unexpected and uncaught system exceptions.
     *
     * @param ex Generic unexpected exception.
     * @return {@link ResponseEntity} containing the {@link ErrorResponse} DTO and HTTP status 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaughtExceptions(Exception ex) {
        String errorId = generateTrackingId();
        
        log.error("[{}] CRITICAL - Uncaught exception detected in API pipeline. Message: '{}'", errorId, ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
            ErrorCode.ERR_INTERNAL_SERVER_ERROR.name(),
            ErrorCode.ERR_INTERNAL_SERVER_ERROR.getDefaultMessage(),
            ex.getMessage(),
            errorId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Generates a standardized unique global tracking ID for incident correlation.
     *
     * @return String representing the unique tracking ID code.
     */
    private String generateTrackingId() {
        return "ERR-" + UUID.randomUUID();
    }
}