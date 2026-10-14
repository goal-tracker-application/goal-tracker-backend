package com.goal_tracker.config.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for {@link JwtAuthenticationFilter}.
 * <p>
 * Evaluates the extraction of the Authorization header, the validation of the Bearer token,
 * setting the authentication in the {@link SecurityContextHolder}, and the correct
 * delegation to the filter chain without roles.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterUnitTest {

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Happy path: Valid Bearer token.
     * <p>
     * <b>Test Design (AAA):</b>
     * <ul>
     * <li><b>Arrange:</b> Configures request with Authorization header and 'Bearer ' prefix.
     * Simulates that {@code jwtProvider} validates and extracts username and user ID.</li>
     * <li><b>Act:</b> Executes {@code doFilterInternal}.</li>
     * <li><b>Assert:</b> Verifies that the user is authenticated in the {@link SecurityContextHolder}
     * and that the request continues down the filter chain.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("doFilterInternal: Should authenticate user in SecurityContext when Bearer token is valid")
    void doFilterInternalShouldEstablishAuthenticationWhenBearerTokenIsValid() throws ServletException, IOException {
        String rawToken = "valid.jwt.token";
        String bearerToken = "Bearer " + rawToken;
        String email = "alex.tracker@example.com";
        Long userId = 42L;

        request.addHeader("Authorization", bearerToken);

        when(jwtProvider.validateToken(rawToken)).thenReturn(true);
        when(jwtProvider.getEmailFromToken(rawToken)).thenReturn(email);
        when(jwtProvider.getUserIdFromToken(rawToken)).thenReturn(userId);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(auth, is(notNullValue()));
        
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal, is(notNullValue()));
        assertThat(principal.id(), is(userId));
        assertThat(principal.email(), is(email));

        verify(jwtProvider, times(1)).validateToken(rawToken);
        verify(jwtProvider, times(1)).getEmailFromToken(rawToken);
        verify(jwtProvider, times(1)).getUserIdFromToken(rawToken);
    }

    /**
     * Adverse case: Absence of Authorization header.
     * <p>
     * Verifies that when the request lacks the 'Authorization' header,
     * the filter performs no validation and passes the request down the chain unauthenticated.
     * </p>
     */
    @Test
    @DisplayName("doFilterInternal: Should continue chain without authenticating when Authorization header is missing")
    void doFilterInternalShouldContinueWithoutAuthenticatingWhenHeaderIsMissing() throws ServletException, IOException {
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(auth, is(nullValue()));
        verifyNoInteractions(jwtProvider);
    }

    /**
     * Adverse case: Header format without Bearer prefix.
     * <p>
     * Ensures that if the Authorization header value uses a different scheme (e.g., "Basic ..."),
     * the filter skips the JWT provider call and leaves the request unauthenticated.
     * </p>
     */
    @Test
    @DisplayName("doFilterInternal: Should ignore header when it does not start with 'Bearer ' prefix")
    void doFilterInternalShouldIgnoreHeaderWhenNotStartingWithBearer() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic YWRtaW46MTIzNA==");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(auth, is(nullValue()));
        verifyNoInteractions(jwtProvider);
    }

    /**
     * Adverse case: Expired, tampered, or invalid token.
     * <p>
     * Verifies that if {@code validateToken} returns {@code false}, the security context
     * remains empty and the request proceeds through the filter chain.
     * </p>
     */
    @Test
    @DisplayName("doFilterInternal: Should continue chain without authenticating when token is invalid or expired")
    void doFilterInternalShouldContinueWithoutAuthenticatingWhenTokenIsInvalid() throws ServletException, IOException {
        String rawToken = "expired.or.invalid.token";
        request.addHeader("Authorization", "Bearer " + rawToken);

        when(jwtProvider.validateToken(rawToken)).thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(auth, is(nullValue()));
        verify(jwtProvider, times(1)).validateToken(rawToken);
        verify(jwtProvider, never()).getEmailFromToken(anyString());
        verify(jwtProvider, never()).getUserIdFromToken(anyString());
    }

    /**
     * Exception resilience: Fault tolerance during parsing/extraction.
     * <p>
     * Ensures that if an unexpected exception occurs during parsing/validation,
     * the exception is caught within the filter and the request continues along the chain without breaking HTTP flow.
     * </p>
     */
    @Test
    @DisplayName("doFilterInternal: Should catch any exception and continue with filter chain")
    void doFilterInternalShouldCatchExceptionAndContinueChain() throws ServletException, IOException {
        String rawToken = "error.token";
        request.addHeader("Authorization", "Bearer " + rawToken);

        when(jwtProvider.validateToken(rawToken)).thenThrow(new RuntimeException("Unexpected parsing error"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(auth, is(nullValue()));
        verify(jwtProvider, times(1)).validateToken(rawToken);
    }
}