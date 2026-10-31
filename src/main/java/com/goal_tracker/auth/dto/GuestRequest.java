package com.goal_tracker.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for guest user creation containing the required CAPTCHA token.
 *
 * @param captchaToken Token provided by the client's CAPTCHA verification widget.
 */
public record GuestRequest(
    @NotBlank(message = "Captcha token is required")
    String captchaToken
) {}