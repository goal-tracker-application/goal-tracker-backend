package com.goal_tracker.auth.service;

import com.goal_tracker.AbstractIntegrationTest;
import com.goal_tracker.auth.dto.LoginRequest;
import com.goal_tracker.auth.dto.PasswordUpdateRequest;
import com.goal_tracker.auth.dto.RegisterRequest;
import com.goal_tracker.auth.exceptions.EmailAreadyInUseException;
import com.goal_tracker.auth.exceptions.IncorrectCurrentPasswordException;
import com.goal_tracker.auth.exceptions.PasswordMismatchException;
import com.goal_tracker.config.security.details.UserPrincipal;
import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.users.entity.User;
import com.goal_tracker.users.repository.UserRepository;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

/**
 * Integration tests for the {@link AuthService} class.
 * <p>
 * These tests verify the behavior of the authentication service in a real application context,
 * including interactions with the database and other components.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceIntegrationTest extends AbstractIntegrationTest {

    /**
     * The authentication service being tested.
     */
    @Autowired
    private AuthService authService;

    /**
     * The user repository used for verifying database state during tests.
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * Password encoder component.
     */
    @Autowired
    private PasswordEncoder passwordEncoder;
    /**
     * Test case for successful user registration.
     * <p>
     * This test verifies that a new regular user can be registered successfully,
     * and that the user is persisted in the database with the correct details.
     * </p>
     */
    @Test
    @DisplayName("register: Should successfully register a new regular user")
    void registerSuccess() {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "user@goaltracker.com",
                "SecurePassword123:",
                "SecurePassword123:"
        );

        // Act
        var response = authService.register(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo("user@goaltracker.com");
        assertThat(response.isGuest()).isFalse();

        boolean exists = userRepository.existsByEmail("user@goaltracker.com");
        assertThat(exists).isTrue();
    }

    /**
     * Test case for registration failure when passwords do not match.
     * <p>
     * This test verifies that the registration process fails when the provided passwords do not match.
     * </p>
     */
    @Test
    @DisplayName("register: Should throw exception when passwords do not match during registration")
    void registerPasswordsDoNotMatch() {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "user@goaltracker.com",
                "SecurePassword123:",
                "DifferentPassword123:"
        );

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(PasswordMismatchException.class) 
        .hasMessageContaining(ErrorCode.ERR_PASSWORD_MISMATCH.getDefaultMessage());

        assertThat(userRepository.existsByEmail("user@goaltracker.com")).isFalse();
    }

    /**
     * Test case for registration failure when the email is already in use.
     * <p>
     * This test verifies that the registration process fails when attempting to register with an email
     * that is already associated with an existing user.
     * </p>
     */
    @Test
    @DisplayName("register: Should throw exception when email is already in use during registration")
    void registerEmailAlreadyInUse() {
        User duplicateUser = new User(
            "duplicate@goaltracker.com",
             passwordEncoder.encode("SecurePassword123:"),
              false,
               OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(duplicateUser);
        userRepository.flush();

        RegisterRequest duplicateRequest = new RegisterRequest(
                "duplicate@goaltracker.com",
                "AnotherPassword123:",
                "AnotherPassword123:"
        );

        // Act & Assert
        assertThatThrownBy(() -> authService.register(duplicateRequest))
                .isInstanceOf(EmailAreadyInUseException.class) 
                .hasMessageContaining(String.format("The email: %s is already in use", "duplicate@goaltracker.com"));
    }

    /**
     * Test case for successful user login with valid credentials.
     * <p>
     * This test verifies that a user can successfully log in with valid email and password,
     * and that the response contains the expected token and user details.
     * </p>
     */
    @Test
    @DisplayName("login: Should successfully login with valid credentials")
    void loginSuccess() {
        User user = new User(
            "user@goaltracker.com",
             passwordEncoder.encode("SecurePassword123:"),
              false,
               OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(user);
        userRepository.flush();

        LoginRequest loginRequest = new LoginRequest(
                "user@goaltracker.com",
                "SecurePassword123:"
        );

        // Act
        var response = authService.login(loginRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo("user@goaltracker.com");
        assertThat(response.isGuest()).isFalse();
    }

    /**
     * Test case for login failure when the user does not exist.
     * <p>
     * This test verifies that the login process fails with a {@link BadCredentialsException}
     * when attempting to log in with an email that is not associated with any user.
     * </p>
     */
    @Test
    @DisplayName("login: Should throw exception when login fails due to non-existent email")
    void loginUserNotFound() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest(
                "nonexistent@goaltracker.com",
                "SomePassword123"
        );

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    /**
     * Test case for login failure when the password is incorrect.
     * <p>
     * This test verifies that the login process fails with a {@link BadCredentialsException}
     * when attempting to log in with a valid email but an incorrect password.
     * </p>
     */
    @Test
    @DisplayName("login: Should throw exception when login fails due to incorrect password")
    void loginIncorrectPassword() {
         User user = new User(
            "user@goaltracker.com",
             passwordEncoder.encode("SecurePassword123:"),
              false,
               OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(user);
        userRepository.flush();

        LoginRequest incorrectLoginRequest = new LoginRequest(
                "user@goaltracker.com",
                "WrongPassword123"
        );

        // Act & Assert
        assertThatThrownBy(() -> authService.login(incorrectLoginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    /**
     * Test case for successful creation of a temporary guest user.
     * <p>
     * This test verifies that a temporary guest user can be created successfully,
     * and that the response contains the expected token, email, and guest flag.
     * </p>
     */
    @Test
    @DisplayName("createGuest: Should successfully create a temporary guest user and return valid auth response")
    void createGuestSuccess() {
        // Act
        var response = authService.createGuest();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.email())
                .isNotNull()
                .startsWith("guest_")
                .endsWith("@goaltracker.local");
        assertThat(response.isGuest()).isTrue();

        boolean existsInDb = userRepository.existsByEmail(response.email());
        assertThat(existsInDb).isTrue();
    }

    /**
     * Test case for successful password update when the current password is correct and new passwords match.
     * <p>
     * This test verifies that a user can successfully update their password when providing the correct current password
     * and matching new passwords. It also checks that the user can log in with the new password afterward.
     * </p>
     */
    @Test
    @DisplayName("updatePassword: Should successfully update password when current password is correct and new passwords match")
    void updatePasswordSuccess() {
        User user = new User(
            "user@goaltracker.com",
             passwordEncoder.encode("SecurePassword123:"),
              false,
               OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(user);
        userRepository.flush();

        authenticateUser(user.getId(), user.getEmail());

        PasswordUpdateRequest updateRequest = new PasswordUpdateRequest(
                "SecurePassword123:",
                "NewSecurePassword456:",
                "NewSecurePassword456:"
        );

        // Act
        authService.updatePassword(updateRequest);

        var loginResponse = authService.login(new LoginRequest(
                "user@goaltracker.com",
                "NewSecurePassword456:"
        ));
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.token()).isNotBlank();
        assertThat(loginResponse.email()).isEqualTo(user.getEmail());
        assertThat(loginResponse.isGuest()).isFalse();
    }

    /**
     * Test case for password update failure when the current password is incorrect.
     * <p>
     * This test verifies that the password update process fails with an {@link IncorrectCurrentPasswordException}
     * when the provided current password does not match the user's existing password.
     * </p>
     */
    @Test
    @DisplayName("updatePassword: Should throw exception when updating password with incorrect current password")
    void updatePasswordIncorrectCurrentPassword() {
        // Arrange
        User user = new User(
            "user@goaltracker.com",
             passwordEncoder.encode("SecurePassword123:"),
              false,
               OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(user);
        userRepository.flush();
        authenticateUser(user.getId(), user.getEmail());

        PasswordUpdateRequest updateRequest = new PasswordUpdateRequest(
                "WrongCurrentPassword",
                "NewSecurePassword456",
                "NewSecurePassword456"
        );

        // Act & Assert
        assertThatThrownBy(() -> authService.updatePassword(updateRequest))
                .isInstanceOf(IncorrectCurrentPasswordException.class)
                .hasMessageContaining(ErrorCode.INVALID_CURRENT_PASSWORD.getDefaultMessage());
    }

    /**
     * Test case for password update failure when the new passwords do not match.
     * <p>
     * This test verifies that the password update process fails with a {@link PasswordMismatchException}
     * when the provided new passwords do not match each other.
     * </p>
     */
    @Test
    @DisplayName("updatePassword: Should throw exception when new passwords do not match during password update")
    void updatePasswordPasswordsMismatch() {
        // Arrange
        User user = new User(
            "user@goaltracker.com",
             passwordEncoder.encode("SecurePassword123:"),
              false,
               OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(user);
        userRepository.flush();
        authenticateUser(user.getId(), user.getEmail());

        PasswordUpdateRequest updateRequest = new PasswordUpdateRequest(
                "SecurePassword123:",
                "NewSecurePassword456:",
                "DifferentPassword789:"
        );

        // Act & Assert
        assertThatThrownBy(() -> authService.updatePassword(updateRequest))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessageContaining(ErrorCode.ERR_PASSWORD_MISMATCH.getDefaultMessage());
    }

    /**
     * Helper method to set up an authenticated user in the Spring Security context 
     * for testing purposes. It creates a mock {@link UserPrincipal} and places a 
     * valid {@link UsernamePasswordAuthenticationToken} into the {@link SecurityContextHolder}.
     *
     * @param userId the unique identifier of the user to authenticate
     * @param email  the email of the user to authenticate
     */
    private void authenticateUser(Long userId, String email) {
        var userPrincipal = new UserPrincipal(userId, email);
        var auth = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, java.util.Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}