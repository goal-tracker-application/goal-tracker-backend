package com.goal_tracker.auth.dto;

/**
 * Data Transfer Object representing the response from the Captcha verification provider.
 */
public record CaptchaResponseDto (
    /**
     * Indicates whether the token is valid.
     */
    boolean success
){

}