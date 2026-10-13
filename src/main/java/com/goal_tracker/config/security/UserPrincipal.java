package com.goal_tracker.config.security;

/**
 * Data structure representing the authenticated user principal stored within the SecurityContext.
 * <p>
 * This model encapsulates essential identification details (such as user ID and username)
 * extracted from validated JWT tokens, providing quick and stateless access to user context
 * across application services and controllers without requiring additional database lookups.
 * </p>
 * UserPrincipal
    * @param id The unique database identifier of the authenticated user
    * @param email The unique login email of the authenticated user
 */
public record UserPrincipal(
        Long id,
        String email
) {}