package com.goal_tracker.config.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for {@link JwtProvider}.
 * <p>
 * Evaluates JWT token generation, parsing, claim extraction (subject and user_id),
 * and validation under symmetric HMAC-SHA signatures without roles.
 * </p>
 */
class JwtProviderUnitTest {

    private JwtProvider jwtProvider;

    private final String invalidSecret = "differentSecretKeyWithSufficientLengthForHmacSha256_1234567890!";
    private final String testSecret = "testSecretKeyWithSuperSufficientLengthForHmacSha256Algorithm_1234567890!";
    private final long testExpirationMs = 3600000;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(testSecret, testExpirationMs);
    }

    /**
     * Happy path validation for token generation and claim extraction.
     */
    @Test
    @DisplayName("generateTokenAndExtractClaims: Should generate a valid JWT token and correctly extract claims")
    void generateTokenAndExtractClaimsShouldProcessDataSuccessfully() {
        Long userId = 42L;
        String username = "alex.tracker";

        String token = jwtProvider.generateToken(userId, username);

        String extractedUsername = jwtProvider.getUsernameFromToken(token);
        Long extractedUserId = jwtProvider.getUserIdFromToken(token);

        assertThat(token, is(notNullValue()));
        assertThat(extractedUsername, is(username));
        assertThat(extractedUserId, instanceOf(Long.class));
        assertThat(extractedUserId, is(42L));
        assertTrue(jwtProvider.validateToken(token));
    }

    /**
     * Adverse case: Invalid or tampered signatures.
     * <p>
     * Ensures that {@code validateToken} returns {@code false} when attempting
     * to validate a token signed with an unauthorized secret key.
     * </p>
     */
    @Test
    @DisplayName("validateToken: Should return false when token signature does not match secret key")
    void validateTokenShouldReturnFalseWhenSignatureIsInvalid() {
        Long userId = 42L;
        String username = "alex.tracker";

        SecretKey foreignKey = Keys.hmacShaKeyFor(invalidSecret.getBytes(StandardCharsets.UTF_8));
        String tokenWithForeignSignature = Jwts.builder()
                .subject(username)
                .claim("user_id", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + testExpirationMs))
                .signWith(foreignKey)
                .compact();

        boolean isValid = jwtProvider.validateToken(tokenWithForeignSignature);

        assertFalse(isValid);
    }

    /**
     * Adverse case: Token expiration.
     * <p>
     * Forces the generation of a token with zero or negative validity time
     * and verifies that the validation suite catches {@code ExpiredJwtException} returning {@code false}.
     * </p>
     */
    @Test
    @DisplayName("validateToken: Should return false when token has expired")
    void validateTokenShouldReturnFalseWhenTokenIsExpired() {
        JwtProvider expiredJwtProvider = new JwtProvider(testSecret, -1000);
        String expiredToken = expiredJwtProvider.generateToken(1L, "expired.user");

        boolean isValid = jwtProvider.validateToken(expiredToken);

        assertFalse(isValid);
    }

    /**
     * Adverse case: Syntactic string malformation.
     * <p>
     * Attempts to validate raw alphanumeric strings that do not conform to the 3-part
     * (Header.Payload.Signature) JWT standard syntax.
     * </p>
     */
    @Test
    @DisplayName("validateToken: Should return false when token is malformed")
    void validateTokenShouldReturnFalseWhenTokenIsMalformed() {
        String malformedToken = "this.is.not.a.valid.jwt";

        boolean isValid = jwtProvider.validateToken(malformedToken);

        assertFalse(isValid);
    }

    /**
     * Adverse case: Empty or null payloads.
     * <p>
     * Verifies perimeter handling when empty strings are passed to the validator.
     * </p>
     */
    @Test
    @DisplayName("validateToken: Should return false when token string is empty")
    void validateTokenShouldReturnFalseWhenTokenIsEmpty() {
        String emptyToken = "";

        boolean isValid = jwtProvider.validateToken(emptyToken);

        assertFalse(isValid);
    }

    /**
     * Parsing resilience: Missing user identifier.
     * <p>
     * Evaluates that {@code getUserIdFromToken} safely returns {@code null} when the claim was omitted.
     * </p>
     */
    @Test
    @DisplayName("getUserIdFromToken: Should return null when user_id claim is missing")
    void getUserIdFromTokenShouldReturnNullWhenClaimIsMissing() {
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutId = Jwts.builder()
                .subject("user.without.id")
                .signWith(key)
                .compact();

        Long extractedId = jwtProvider.getUserIdFromToken(tokenWithoutId);

        assertThat(extractedId, is(nullValue()));
    }
}