package com.goal_tracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) that encapsulates the login credentials provided by a user.
 * <p>
 * This model is used to receive and validate access credentials (email and password)
 * sent from the client, ensuring they are not empty before being processed by the authentication system.
 * </p>
 *
 * @param email    User's email address used for identification.
 * @param password User's plain text password.
 */
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Must provide a valid email address")
    @Size(max = 320, message = "Email cannot exceed 320 characters")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {}