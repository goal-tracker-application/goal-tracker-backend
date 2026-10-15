package com.goal_tracker.auth.dto;

import com.goal_tracker.auth.validation.ValidPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user registration.
 *
 * @param email           Unique email address used for login (max 320 characters).
 * @param password        Plain text password (between 8 and 72 characters due to BCrypt truncation limits).
 * @param confirmPassword Password confirmation for cross-validation.
 */
public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Must provide a valid email address")
    @Size(max = 320, message = "Email cannot exceed 320 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @ValidPassword
    String password,

    @NotBlank(message = "Password confirmation is required")
    String confirmPassword
) {
    /**
     * Utility method to check if the entered passwords match.
     * Can be called explicitly from the Service layer.
     *
     * @return {@code true} if both passwords are identical.
     */
    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}