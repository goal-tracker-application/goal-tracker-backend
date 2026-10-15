package com.goal_tracker.auth.controller;

import com.goal_tracker.auth.dto.AuthResponse;
import com.goal_tracker.auth.dto.LoginRequest;
import com.goal_tracker.auth.dto.PasswordUpdateRequest;
import com.goal_tracker.auth.dto.RegisterRequest;
import com.goal_tracker.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user authentication, registration, and session management.
 * <p>
 * Exposes endpoints for user registration, authentication, guest session creation,
 * and secure password updates.
 * </p>
 *
 * @see AuthService
 */
@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = "Authentication", description = "Endpoints for managing user authentication and sessions")
public class AuthController {

    private final AuthService authService;

    /**
     * Constructor for dependency injection of the authentication service.
     *
     * @param authService Service handling authentication business logic.
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user.
     *
     * @param request DTO containing registration details.
     * @return {@link ResponseEntity} with HTTP 201 (Created) and the {@link AuthResponse}.
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Validates registration data, creates a regular user account, and returns an access JWT."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User successfully registered and authenticated."),
        @ApiResponse(responseCode = "400", description = "Validation error, passwords mismatch, or email already in use."),
        @ApiResponse(responseCode = "409", description = "Email address is already registered.")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates an existing user.
     *
     * @param request DTO containing login credentials.
     * @return {@link ResponseEntity} with HTTP 200 (OK) and the {@link AuthResponse}.
     */
    @PostMapping("/login")
    @Operation(
        summary = "User login",
        description = "Authenticates a user via email and password, returning a signed JWT access token."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authentication successful. Returns the JWT access token."),
        @ApiResponse(responseCode = "400", description = "Validation error in the request payload."),
        @ApiResponse(responseCode = "401", description = "Invalid credentials.")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a temporary guest user.
     *
     * @return {@link ResponseEntity} with HTTP 201 (Created) and the guest's {@link AuthResponse}.
     */
    @PostMapping("/guest")
    @Operation(
        summary = "Create guest session",
        description = "Provisions a temporary guest user account and returns an access JWT."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Guest session successfully created.")
    })
    public ResponseEntity<AuthResponse> createGuest() {
        AuthResponse response = authService.createGuest();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates the password of the currently authenticated user.
     *
     * @param request DTO containing current password, new password, and confirmation.
     * @return {@link ResponseEntity} with HTTP 204 (No Content) on success.
     */
    @PatchMapping("/password")
    @Operation(
        summary = "Update password",
        description = "Allows an authenticated user to change their password by validating the current one."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password successfully updated."),
        @ApiResponse(responseCode = "400", description = "Validation error, current password incorrect, or new passwords mismatch.")
    })
    public ResponseEntity<Void> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        authService.updatePassword(request);
        return ResponseEntity.noContent().build();
    }
}