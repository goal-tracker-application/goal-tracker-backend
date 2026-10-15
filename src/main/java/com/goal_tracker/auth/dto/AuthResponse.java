package com.goal_tracker.auth.dto;

/**
 * Data Transfer Object (DTO) containing the authentication response details,
 * including the generated JWT token, user email, and guest status.
 *
 * @param token   The JWT access token.
 * @param email   The user's email.
 * @param isGuest Flag indicating if the user is a temporary guest.
 */
public record AuthResponse(
    String token,
    String email,
    boolean isGuest
) {}